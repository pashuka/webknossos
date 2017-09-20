/**
 * model.js
 * @flow
 */

import _ from "lodash";
import Store from "oxalis/store";
import type {
  BoundingBoxObjectType,
  SettingsType,
  EdgeType,
  CommentType,
  SegmentationDataLayerType,
  TracingTypeTracingType,
  ElementClassType,
} from "oxalis/store";
import type { UrlManagerState } from "oxalis/controller/url_manager";
import {
  setDatasetAction,
  setViewModeAction,
  setControlModeAction,
} from "oxalis/model/actions/settings_actions";
import {
  setActiveNodeAction,
  initializeSkeletonTracingAction,
} from "oxalis/model/actions/skeletontracing_actions";
import { initializeVolumeTracingAction } from "oxalis/model/actions/volumetracing_actions";
import { setTaskAction } from "oxalis/model/actions/task_actions";
import {
  setPositionAction,
  setZoomStepAction,
  setRotationAction,
} from "oxalis/model/actions/flycam_actions";
import { saveNowAction } from "oxalis/model/actions/save_actions";
import window from "libs/window";
import Utils from "libs/utils";
import Binary from "oxalis/model/binary";
import ConnectionInfo from "oxalis/model/binarydata_connection_info";
import { getIntegerZoomStep } from "oxalis/model/accessors/flycam_accessor";
import constants, { Vector3Indicies, ControlModeEnum, ModeValues } from "oxalis/constants";
import type { Vector3, Point3, ControlModeType } from "oxalis/constants";
import Request from "libs/request";
import Toast from "libs/toast";
import ErrorHandling from "libs/error_handling";
import WkLayer from "oxalis/model/binary/layers/wk_layer";
import NdStoreLayer from "oxalis/model/binary/layers/nd_store_layer";
import update from "immutability-helper";
import UrlManager from "oxalis/controller/url_manager";
import type { APIDatasetType, APIAnnotationType } from "admin/api_flow_types";

export type ServerNodeType = {
  id: number,
  position: Point3,
  rotation: Point3,
  bitDepth: number,
  viewport: number,
  resolution: number,
  radius: number,
  createdTimestamp: number,
};

export type ServerBranchPointType = {
  createdTimestamp: number,
  nodeId: number,
};

export type ServerColorType = {
  r: number,
  g: number,
  b: number,
  a: number,
};

type ServerSkeletonTracingTreeType = {
  branchPoints: Array<ServerBranchPointType>,
  color: ?ServerColorType,
  comments: Array<CommentType>,
  edges: Array<EdgeType>,
  name: string,
  nodes: Array<ServerNodeType>,
  treeId: number,
};

type ServerTracingBaseType = {
  id: string,
  boundingBox?: BoundingBoxObjectType,
  createdTimestamp: number,
  editPosition: Point3,
  editRotation: Point3,
  version: number,
  zoomLevel: number,
};

export type ServerSkeletonTracingType = ServerTracingBaseType & {
  activeNodeId?: number,
  trees: Array<ServerSkeletonTracingTreeType>,
};

export type ServerVolumeTracingType = ServerTracingBaseType & {
  activeSegmentId?: number,
  elementClass: ElementClassType,
  fallbackLayer?: string,
  largestSegmentId: number,
};

type ServerTracingType = ServerSkeletonTracingType | ServerVolumeTracingType;

// TODO: Non-reactive
export class OxalisModel {
  HANDLED_ERROR = "error_was_handled";

  connectionInfo: ConnectionInfo;
  binary: {
    [key: string]: Binary,
  };
  lowerBoundary: Vector3;
  upperBoundary: Vector3;

  async fetch(
    tracingType: TracingTypeTracingType,
    annotationId: string,
    controlMode: ControlModeType,
    initialFetch: boolean,
  ) {
    Store.dispatch(setControlModeAction(controlMode));

    let annotation: ?APIAnnotationType;
    let datasetName;
    if (controlMode === ControlModeEnum.TRACE) {
      // Include /readOnly part whenever it is in the pathname
      const isReadOnly = window.location.pathname.endsWith("/readOnly");
      const readOnlyPart = isReadOnly ? "readOnly/" : "";
      const infoUrl = `/annotations/${tracingType}/${annotationId}/${readOnlyPart}info`;
      annotation = await Request.receiveJSON(infoUrl);
      datasetName = annotation.dataSetName;

      let error;
      if (annotation.error) {
        ({ error } = annotation);
      } else if (!annotation.restrictions.allowAccess) {
        error = "You are not allowed to access this tracing";
      }

      if (error) {
        Toast.error(error);
        throw this.HANDLED_ERROR;
      }

      ErrorHandling.assertExtendContext({
        task: annotation.id,
      });

      Store.dispatch(setTaskAction(annotation.task));
    } else {
      // In View mode, the annotationId is actually the dataSetName
      // as there is no annotation and no tracing!
      datasetName = annotationId;
    }

    await this.initializeDataset(datasetName);

    // Fetch the actual tracing from the datastore, if there is an annotation
    let tracing: ?ServerTracingType;
    if (annotation != null) {
      tracing = await Request.receiveJSON(
        `${annotation.dataStore.url}/data/tracings/${annotation.content.typ}/${annotation.content
          .id}`,
      );
      tracing.id = annotation.content.id;
    }

    // Only initialize the model once.
    // There is no need to reinstantiate the binaries if the dataset didn't change.
    if (initialFetch) {
      this.initializeModel(tracing);
      if (tracing != null) Store.dispatch(setZoomStepAction(tracing.zoomLevel));
    }
    // There is no need to initialize the tracing if there is no tracing (View mode).
    if (annotation != null && tracing != null) {
      this.initializeTracing(annotation, tracing);
    }
  }

  determineAllowedModes(settings: SettingsType) {
    // The order of allowedModes should be independent from the server and instead be similar to ModeValues
    let allowedModes = _.intersection(ModeValues, settings.allowedModes);

    const colorLayer = _.find(Store.getState().dataset.dataLayers, { category: "color" });
    if (colorLayer != null && colorLayer.elementClass !== "uint8") {
      allowedModes = allowedModes.filter(mode => !constants.MODES_ARBITRARY.includes(mode));
    }

    let preferredMode = null;
    if (settings.preferredMode != null) {
      const modeId = settings.preferredMode;
      if (allowedModes.includes(modeId)) {
        preferredMode = modeId;
      }
    }

    return { preferredMode, allowedModes };
  }

  initializeTracing(annotation: APIAnnotationType, tracing: ServerTracingType) {
    // This method is not called for the View mode
    // TODO Maybe applyState should be called in View mode as well
    const { allowedModes, preferredMode } = this.determineAllowedModes(annotation.settings);
    _.extend(annotation.settings, { allowedModes, preferredMode });

    const isVolume = annotation.content.typ === "volume";
    const controlMode = Store.getState().temporaryConfiguration.controlMode;
    if (controlMode === ControlModeEnum.TRACE) {
      if (isVolume) {
        ErrorHandling.assert(
          this.getSegmentationBinary() != null,
          "Volume is allowed, but segmentation does not exist",
        );
        const volumeTracing: ServerVolumeTracingType = (tracing: any);
        Store.dispatch(initializeVolumeTracingAction(annotation, volumeTracing));
      } else {
        const skeletonTracing: ServerSkeletonTracingType = (tracing: any);
        Store.dispatch(initializeSkeletonTracingAction(annotation, skeletonTracing));
      }
    }

    // Initialize 'flight', 'oblique' or 'orthogonal'/'volume' mode
    if (allowedModes.length === 0) {
      Toast.error("There was no valid allowed tracing mode specified.");
    } else {
      const mode = preferredMode || UrlManager.initialState.mode || allowedModes[0];
      Store.dispatch(setViewModeAction(mode));
    }

    this.applyState(UrlManager.initialState, tracing);
  }

  async initializeDataset(datasetName: string) {
    const dataset: APIDatasetType = await Request.receiveJSON(`/api/datasets/${datasetName}`);

    let error;
    if (!dataset) {
      error = "Selected dataset doesn't exist";
    } else if (!dataset.dataSource.dataLayers) {
      error = `Please, double check if you have the dataset '${datasetName}' imported.`;
    }

    if (error) {
      Toast.error(error);
      throw this.HANDLED_ERROR;
    }

    // Make sure subsequent fetch calls are always for the same dataset
    if (!_.isEmpty(this.binary)) {
      ErrorHandling.assert(
        _.isEqual(dataset, Store.getState().dataset),
        "Model.fetch was called for a task with another dataset, without reloading the page.",
      );
    }

    ErrorHandling.assertExtendContext({
      dataSet: dataset.dataSource.id.name,
    });

    Store.dispatch(setDatasetAction(dataset));
  }

  initializeModel(tracing: ?ServerTracingType) {
    const { dataStore } = Store.getState().dataset;

    const LayerClass = (() => {
      switch (dataStore.typ) {
        case "webknossos-store":
          return WkLayer;
        case "ndstore":
          return NdStoreLayer;
        default:
          throw new Error(`Unknown datastore type: ${dataStore.typ}`);
      }
    })();

    const layers = this.getLayerInfos(tracing).map(
      layerInfo => new LayerClass(layerInfo, dataStore),
    );

    this.connectionInfo = new ConnectionInfo();
    this.binary = {};
    for (const layer of layers) {
      const maxLayerZoomStep = Math.log(Math.max(...layer.resolutions)) / Math.LN2;
      this.binary[layer.name] = new Binary(layer, maxLayerZoomStep, this.connectionInfo);
    }

    this.buildMappingsObject();

    if (this.getColorBinaries().length === 0) {
      Toast.error("No data available! Something seems to be wrong with the dataset.");
      throw this.HANDLED_ERROR;
    }

    this.computeBoundaries();
  }

  // For now, since we have no UI for this
  buildMappingsObject() {
    const segmentationBinary = this.getSegmentationBinary();

    if (segmentationBinary != null) {
      window.mappings = {
        getAll() {
          return segmentationBinary.mappings.getMappingNames();
        },
        getActive() {
          return segmentationBinary.activeMapping;
        },
        activate(mapping) {
          return segmentationBinary.setActiveMapping(mapping);
        },
      };
    }
  }

  getColorBinaries() {
    return _.filter(this.binary, binary => binary.category === "color");
  }

  getSegmentationBinary() {
    return _.find(this.binary, binary => binary.category === "segmentation");
  }

  getBinaryByName(name: string) {
    return this.binary[name];
  }

  getLayerInfos(tracing: ?ServerTracingType) {
    // Overwrite or extend layers with volumeTracingLayer
    const layers = _.clone(Store.getState().dataset.dataLayers);
    if (tracing == null || tracing.elementClass == null) {
      return layers;
    }

    // This code will only be executed for volume tracings as only those have a dataLayer.
    // The tracing always contains the layer information for the user segmentation.
    // layers (dataset.dataLayers) contains information about all existing layers of the dataset.
    // Two possible cases:
    // 1) No segmentation exists yet: In that case layers doesn't contain the dataLayer.
    // 2) Segmentation exists: In that case layers already contains dataLayer and the fallbackLayer
    //    property specifies its name, to be able to merge the two layers
    const fallbackLayer = tracing.fallbackLayer != null ? tracing.fallbackLayer : null;
    const existingLayerIndex = _.findIndex(layers, layer => layer.name === fallbackLayer);
    const existingLayer = layers[existingLayerIndex];

    const tracingLayer = {
      name: tracing.id,
      category: "segmentation",
      boundingBox: {
        topLeft: [
          tracing.boundingBox.topLeft.x,
          tracing.boundingBox.topLeft.y,
          tracing.boundingBox.topLeft.z,
        ],
        width: tracing.boundingBox.width,
        height: tracing.boundingBox.height,
        depth: tracing.boundingBox.depth,
      },
      resolutions: [1],
      elementClass: tracing.elementClass,
      mappings: existingLayer != null ? existingLayer.mappings : [],
      largestSegmentId: tracing.largestSegmentId,
    };

    if (existingLayer != null) {
      layers[existingLayerIndex] = tracingLayer;
    } else {
      layers.push(tracingLayer);
    }
    return layers;
  }

  shouldDisplaySegmentationData(): boolean {
    const segmentationOpacity = Store.getState().datasetConfiguration.segmentationOpacity;
    if (segmentationOpacity === 0) {
      return false;
    }
    const currentViewMode = Store.getState().temporaryConfiguration.viewMode;

    // Currently segmentation data can only be displayed in orthogonal and volume mode
    const canModeDisplaySegmentationData = constants.MODES_PLANE.includes(currentViewMode);
    return this.getSegmentationBinary() != null && canModeDisplaySegmentationData;
  }

  canDisplaySegmentationData(): boolean {
    return getIntegerZoomStep(Store.getState()) <= 1;
  }

  computeBoundaries() {
    this.lowerBoundary = [Infinity, Infinity, Infinity];
    this.upperBoundary = [-Infinity, -Infinity, -Infinity];

    for (const key of Object.keys(this.binary)) {
      const binary = this.binary[key];
      for (const i of Vector3Indicies) {
        this.lowerBoundary[i] = Math.min(this.lowerBoundary[i], binary.lowerBoundary[i]);
        this.upperBoundary[i] = Math.max(this.upperBoundary[i], binary.upperBoundary[i]);
      }
    }
  }

  applyState(state: UrlManagerState, tracing: ServerTracingType) {
    Store.dispatch(
      setPositionAction(state.position || Utils.point3ToVector3(tracing.editPosition)),
    );
    if (state.zoomStep != null) {
      Store.dispatch(setZoomStepAction(state.zoomStep));
    }
    const rotation = state.rotation || Utils.point3ToVector3(tracing.editRotation);
    if (rotation != null) {
      Store.dispatch(setRotationAction(rotation));
    }

    if (state.activeNode != null) {
      Store.dispatch(setActiveNodeAction(state.activeNode));
    }
  }

  stateSaved() {
    const state = Store.getState();
    const storeStateSaved = !state.save.isBusy && state.save.queue.length === 0;
    const pushQueuesSaved = _.reduce(
      this.binary,
      (saved, binary) => saved && binary.pushQueue.stateSaved(),
      true,
    );
    return storeStateSaved && pushQueuesSaved;
  }

  save = async () => {
    Store.dispatch(saveNowAction());
    while (!this.stateSaved()) {
      // eslint-disable-next-line no-await-in-loop
      await Utils.sleep(500);
    }
  };
}

// export the model as a singleton
export default new OxalisModel();
