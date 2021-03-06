/**
 * volumetracing_plane_controller.js
 * @flow
 */

import _ from "lodash";

import {
  ContourModeEnum,
  type OrthoView,
  OrthoViews,
  type Point2,
  VolumeToolEnum,
} from "oxalis/constants";
import { calculateGlobalPos } from "oxalis/controller/viewmodes/plane_controller";
import {
  createCellAction,
  setToolAction,
  startEditingAction,
  addToLayerAction,
  finishEditingAction,
  hideBrushAction,
  setContourTracingMode,
  cycleToolAction,
  copySegmentationLayerAction,
  setActiveCellAction,
} from "oxalis/model/actions/volumetracing_actions";
import { getPosition, getRequestLogZoomStep } from "oxalis/model/accessors/flycam_accessor";
import {
  getVolumeTool,
  getContourTracingMode,
  enforceVolumeTracing,
} from "oxalis/model/accessors/volumetracing_accessor";
import { movePlaneFlycamOrthoAction, setPositionAction } from "oxalis/model/actions/flycam_actions";
import Model from "oxalis/model";
import Store from "oxalis/store";
import * as Utils from "libs/utils";

const simulateTracing = async (): Promise<void> => {
  Store.dispatch(setToolAction(VolumeToolEnum.TRACE));

  const controls = getPlaneMouseControls(OrthoViews.PLANE_XY);
  let pos = (x, y) => ({ x, y });

  controls.leftMouseDown(pos(100, 100), OrthoViews.PLANE_XY, ({}: any));
  await Utils.sleep(100);
  const nullDelta = { x: 0, y: 0 };
  controls.leftDownMove(nullDelta, pos(200, 100));
  await Utils.sleep(100);
  controls.leftDownMove(nullDelta, pos(200, 200));
  await Utils.sleep(100);
  controls.leftDownMove(nullDelta, pos(100, 200));
  await Utils.sleep(100);
  controls.leftDownMove(nullDelta, pos(100, 100));
  controls.leftMouseUp();
  await Utils.sleep(100);
  pos = _.clone(getPosition(Store.getState().flycam));
  pos[2]++;
  Store.dispatch(setPositionAction(pos));
  await Utils.sleep(100);
  await simulateTracing();
};

export function getPlaneMouseControls(_planeId: OrthoView): * {
  return {
    leftDownMove: (delta: Point2, pos: Point2) => {
      const { tracing } = Store.getState();
      const volumeTracing = enforceVolumeTracing(tracing);
      const tool = getVolumeTool(volumeTracing);
      const contourTracingMode = getContourTracingMode(volumeTracing);

      if (tool === VolumeToolEnum.MOVE) {
        const state = Store.getState();
        const { activeViewport } = state.viewModeData.plane;
        const v = [-delta.x, -delta.y, 0];
        Store.dispatch(movePlaneFlycamOrthoAction(v, activeViewport, true));
      }

      if (
        (tool === VolumeToolEnum.TRACE || tool === VolumeToolEnum.BRUSH) &&
        (contourTracingMode === ContourModeEnum.DRAW ||
          contourTracingMode === ContourModeEnum.DRAW_OVERWRITE)
      ) {
        Store.dispatch(addToLayerAction(calculateGlobalPos(pos)));
      }
    },

    leftMouseDown: (pos: Point2, plane: OrthoView, event: MouseEvent) => {
      const tool = Utils.enforce(getVolumeTool)(Store.getState().tracing.volume);

      if (!event.shiftKey && (tool === VolumeToolEnum.TRACE || tool === VolumeToolEnum.BRUSH)) {
        if (event.ctrlKey) {
          Store.dispatch(setContourTracingMode(ContourModeEnum.DRAW));
        } else {
          Store.dispatch(setContourTracingMode(ContourModeEnum.DRAW_OVERWRITE));
        }
        Store.dispatch(startEditingAction(calculateGlobalPos(pos), plane));
      }
    },

    leftMouseUp: () => {
      const tool = Utils.enforce(getVolumeTool)(Store.getState().tracing.volume);

      Store.dispatch(setContourTracingMode(ContourModeEnum.IDLE));

      if (tool === VolumeToolEnum.TRACE || tool === VolumeToolEnum.BRUSH) {
        Store.dispatch(finishEditingAction());
      }
    },

    rightDownMove: (delta: Point2, pos: Point2) => {
      const { tracing } = Store.getState();
      const volumeTracing = enforceVolumeTracing(tracing);
      const tool = getVolumeTool(volumeTracing);
      const contourTracingMode = getContourTracingMode(volumeTracing);

      if (
        (tool === VolumeToolEnum.TRACE || tool === VolumeToolEnum.BRUSH) &&
        (contourTracingMode === ContourModeEnum.DELETE_FROM_ACTIVE_CELL ||
          contourTracingMode === ContourModeEnum.DELETE_FROM_ANY_CELL)
      ) {
        Store.dispatch(addToLayerAction(calculateGlobalPos(pos)));
      }
    },

    rightMouseDown: (pos: Point2, plane: OrthoView, event: MouseEvent) => {
      const tool = Utils.enforce(getVolumeTool)(Store.getState().tracing.volume);

      if (!event.shiftKey && (tool === VolumeToolEnum.TRACE || tool === VolumeToolEnum.BRUSH)) {
        if (event.ctrlKey) {
          Store.dispatch(setContourTracingMode(ContourModeEnum.DELETE_FROM_ANY_CELL));
        } else {
          Store.dispatch(setContourTracingMode(ContourModeEnum.DELETE_FROM_ACTIVE_CELL));
        }
        Store.dispatch(startEditingAction(calculateGlobalPos(pos), plane));
      }
    },

    rightMouseUp: () => {
      const tool = Utils.enforce(getVolumeTool)(Store.getState().tracing.volume);

      Store.dispatch(setContourTracingMode(ContourModeEnum.IDLE));

      if (tool === VolumeToolEnum.TRACE || tool === VolumeToolEnum.BRUSH) {
        Store.dispatch(finishEditingAction());
        Store.dispatch(setContourTracingMode(ContourModeEnum.IDLE));
      }
    },

    leftClick: (pos: Point2, plane: OrthoView, event: MouseEvent) => {
      if (event.shiftKey) {
        const segmentation = Model.getSegmentationLayer();
        if (!segmentation) {
          return;
        }
        const cellId = segmentation.cube.getMappedDataValue(
          calculateGlobalPos(pos),
          getRequestLogZoomStep(Store.getState()),
        );
        if (cellId > 0) {
          Store.dispatch(setActiveCellAction(cellId));
        }
      }
    },

    out: () => {
      Store.dispatch(hideBrushAction());
    },
  };
}

export function getKeyboardControls() {
  return {
    c: () => Store.dispatch(createCellAction()),
    w: () => {
      Store.dispatch(cycleToolAction());
    },
    "1": () => {
      Store.dispatch(cycleToolAction());
    },
    v: () => {
      Store.dispatch(copySegmentationLayerAction());
    },
    "shift + v": () => {
      Store.dispatch(copySegmentationLayerAction(true));
    },
  };
}
