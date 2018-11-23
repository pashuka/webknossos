// @flow

import { type Saga, _all, _call, _cancel, fork, take } from "oxalis/model/sagas/effect-generators";
import { alert } from "libs/window";
import {
  editVolumeLayerAsync,
  disallowVolumeTracingWarning,
  watchVolumeTracingAsync,
} from "oxalis/model/sagas/volumetracing_saga";
import {
  pushAnnotationAsync,
  saveTracingAsync,
  collectUndoStates,
} from "oxalis/model/sagas/save_saga";
import {
  warnAboutSegmentationOpacity,
  watchAnnotationAsync,
} from "oxalis/model/sagas/annotation_saga";
import { watchDataRelevantChanges } from "oxalis/model/sagas/prefetch_saga";
import { watchSkeletonTracingAsync } from "oxalis/model/sagas/skeletontracing_saga";
import handleMeshChanges from "oxalis/model/sagas/handle_mesh_changes";
import isosurfaceSaga from "oxalis/model/sagas/isosurface_saga";
import watchPushSettingsAsync from "oxalis/model/sagas/settings_saga";

export default function* rootSaga(): Saga<void> {
  while (true) {
    const task = yield* fork(restartableSaga);
    yield* take("RESTART_SAGA");
    yield _cancel(task);
  }
}

function* restartableSaga(): Saga<void> {
  try {
    yield _all([
      _call(warnAboutSegmentationOpacity),
      _call(watchPushSettingsAsync),
      _call(watchSkeletonTracingAsync),
      _call(collectUndoStates),
      _call(saveTracingAsync),
      _call(pushAnnotationAsync),
      _call(editVolumeLayerAsync),
      _call(disallowVolumeTracingWarning),
      _call(watchVolumeTracingAsync),
      _call(watchAnnotationAsync),
      _call(watchDataRelevantChanges),
      _call(isosurfaceSaga),
      _call(handleMeshChanges),
    ]);
  } catch (err) {
    console.error(err);
    alert(`\
Internal error.
Please reload the page to avoid losing data.

${err} ${err.stack}`);
  }
}
