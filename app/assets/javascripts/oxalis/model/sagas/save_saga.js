/*
 * save_saga.js
 * @flow
 */
 import _ from "lodash";
 import $ from "jquery";
import { call, put, take, select, race } from "redux-saga/effects";
import { delay } from "redux-saga";
import Request from "libs/request";
import { shiftSaveQueueAction, setSaveBusyAction, setLastSaveTimestampAction } from "oxalis/model/actions/save_actions";
import { setVersionNumber } from "oxalis/model/actions/skeletontracing_actions";
import messages from "messages";
import type { UpdateAction } from "oxalis/model/sagas/update_actions";
import Utils from "libs/utils";

const PUSH_THROTTLE_TIME = 30000; // 30s
const SAVE_RETRY_WAITING_TIME = 5000;

export function* pushAnnotationAsync(): Generator<*, *, *> {
  yield take("INITIALIZE_SKELETONTRACING");
  yield put(setLastSaveTimestampAction());
  while (true) {
    const pushAction = yield take("PUSH_SAVE_QUEUE");
    yield put(setSaveBusyAction(true));
    while (yield select(state => state.save.queue.length > 0)) {
      if (!pushAction.pushNow) {
        yield race({
          timeout: call(delay, PUSH_THROTTLE_TIME),
          forcePush: take("SAVE_NOW"),
        });
      }
      const batch = yield select(state => state.save.queue);
      const version = yield select(state => state.skeletonTracing.version);
      const tracingType = yield select(state => state.skeletonTracing.tracingType);
      const tracingId = yield select(state => state.skeletonTracing.id);
      try {
        yield call(Request.sendJSONReceiveJSON,
          `/annotations/${tracingType}/${tracingId}?version=${version + 1}`, {
            method: "PUT",
            data: batch,
          });
        yield put(setVersionNumber(version + 1));
        yield put(setLastSaveTimestampAction());
        yield put(shiftSaveQueueAction(batch.length));
        yield call(toggleErrorHighlighting, false);
      } catch (error) {
        yield call(toggleErrorHighlighting, true);
        if (error.status >= 400 && error.status < 500) {
          // app.router.off("beforeunload");
          // HTTP Code 409 'conflict' for dirty state
          if (error.status === 409) {
            // eslint-disable-next-line no-alert
            alert(messages["save.failed_simultaneous_tracing"]);
          } else {
            // eslint-disable-next-line no-alert
            alert(messages["save.failed_client_error"]);
          }
          // app.router.reload();
          return;
        }
        yield Utils.sleep(SAVE_RETRY_WAITING_TIME);
      }
    }
    yield put(setSaveBusyAction(false));
  }
}

function toggleErrorHighlighting(state: boolean) {
  $("body").toggleClass("save-error", state);
}

export function compactUpdateActions(updateActions: Array<UpdateAction>): Array<UpdateAction> {
  let result = updateActions;

  // Remove all but the last updateTracing update actions
  const updateTracingUpdateActions = result.filter(ua => ua.action === "updateTracing");
  if (updateTracingUpdateActions.length > 1) {
    result = _.without(result, ...updateTracingUpdateActions.slice(0, -1));
  }

  // // Detect moved nodes
  // const movedNodes = [];
  // for (const createUA of result) {
  //   if (createUA.action === "createNode") {
  //     const deleteUA = result.find(ua =>
  //       ua.action === "deleteNode" &&
  //       ua.value.id === createUA.value.id &&
  //       ua.value.treeId !== createUA.value.treeId);
  //     if (deleteUA != null) {
  //       movedNodes.push([createUA, deleteUA]);
  //     }
  //   }
  // }
  // for (const [createUA, deleteUA] of movedNodes) {
  //   moveTreeComponent(deleteUA.value.treeId, createUA.value.treeId, [createUA.value.id]);
  // }

  return result;
}
