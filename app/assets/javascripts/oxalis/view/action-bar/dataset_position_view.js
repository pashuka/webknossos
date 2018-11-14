// @flow
import React, { PureComponent } from "react";
import type { OxalisState, Flycam } from "oxalis/store";
import { connect } from "react-redux";
import Clipboard from "clipboard-js";
import type { Mode } from "oxalis/constants";
import constants from "oxalis/constants";
import Toast from "libs/toast";
import { V3 } from "libs/mjs";
import Store from "oxalis/store";
import { setPositionAction, setRotationAction } from "oxalis/model/actions/flycam_actions";
import { getPosition, getRotation } from "oxalis/model/accessors/flycam_accessor";
import { Input, Tooltip, Icon } from "antd";
import { Vector3Input } from "libs/vector_input";
import ButtonComponent from "oxalis/view/components/button_component";
import message from "messages";

import SceneController from "oxalis/controller/scene_controller";
import { doWithToken } from "admin/admin_rest_api";
import Request from "libs/request";
import Model from "oxalis/model";

type Props = {
  flycam: Flycam,
  viewMode: Mode,
};

class DatasetPositionView extends PureComponent<Props> {
  copyPositionToClipboard = async () => {
    const position = V3.floor(getPosition(this.props.flycam)).join(", ");
    await Clipboard.copy(position);
    Toast.success("Position copied to clipboard");
  };

  copyRotationToClipboard = async () => {
    const rotation = V3.round(getRotation(this.props.flycam)).join(", ");
    await Clipboard.copy(rotation);
    Toast.success("Rotation copied to clipboard");
  };

  handleChangePosition = position => {
    Store.dispatch(setPositionAction(position));
  };

  handleChangeRotation = rotation => {
    Store.dispatch(setRotationAction(rotation));
  };

  loadIsosurface = async () => {
    const voxelDimensions = [2, 2, 2];
    const cubeSize = 256;
    const position = V3.floor(getPosition(this.props.flycam));
    const layer = Model.getSegmentationLayer();
    const segmentId = layer.cube.getDataValue(position, null, 1);
    const zoomStep = Math.floor(this.props.flycam.zoomStep);
    const offset = (cubeSize << zoomStep) / 2;

    const responseBuffer = await doWithToken(token =>
      Request.sendJSONReceiveArraybuffer(
        `/data/datasets/Connectomics_Department/ROI2017_wkw/layers/segmentation/isosurface?token=${token}`,
        {
          data: {
            // same as with regular data requests
            position: [position[0] - offset, position[1] - offset, position[2] - offset],
            cubeSize,
            zoomStep,

            // segment to build isosurface for
            segmentId,
            // name of mapping to apply before building isosurface (optional)
            mapping: layer.activeMapping,
            // "size" of each voxel (i.e., only every nth voxel is considered in each dimension)
            voxelDimensions,
          },
        },
      ),
    );
    const vertices = new Float32Array(responseBuffer);
    SceneController.addIsosurface(vertices);
  };

  render() {
    const position = V3.floor(getPosition(this.props.flycam));
    const rotation = V3.round(getRotation(this.props.flycam));
    const isArbitraryMode = constants.MODES_ARBITRARY.includes(this.props.viewMode);

    return (
      <div>
        <ButtonComponent onClick={this.loadIsosurface}>load isosurface</ButtonComponent>
        <Tooltip title={message["tracing.copy_position"]} placement="bottomLeft">
          <div>
            <Input.Group compact>
              <ButtonComponent onClick={this.copyPositionToClipboard}>
                <Icon type="pushpin" style={{ transform: "rotate(45deg)" }} />
              </ButtonComponent>
              <Vector3Input
                value={position}
                onChange={this.handleChangePosition}
                // The input field should be able to show at least xxxxx, yyyyy, zzzzz
                // without scrolling
                style={{ maxWidth: 140, textAlign: "center" }}
              />
            </Input.Group>
          </div>
        </Tooltip>
        {isArbitraryMode ? (
          <Tooltip title={message["tracing.copy_rotation"]} placement="bottomLeft">
            <div style={{ marginLeft: 10 }}>
              <Input.Group compact>
                <ButtonComponent onClick={this.copyRotationToClipboard}>
                  <Icon type="reload" />
                </ButtonComponent>
                <Vector3Input
                  value={rotation}
                  onChange={this.handleChangeRotation}
                  style={{ width: 100 }}
                />
              </Input.Group>
            </div>
          </Tooltip>
        ) : null}
      </div>
    );
  }
}

function mapStateToProps(state: OxalisState): Props {
  return {
    flycam: state.flycam,
    viewMode: state.temporaryConfiguration.viewMode,
  };
}

export default connect(mapStateToProps)(DatasetPositionView);
