/*
 * color_generator.js
 * @flow
 */
import _ from "lodash";

import type { Vector3 } from "oxalis/constants";
import { chunk3 } from "oxalis/model/helpers/chunk";

const rawRgbs = [
  173,
  36,
  13,
  0,
  0,
  255,
  217,
  133,
  18,
  0,
  255,
  193,
  255,
  214,
  61,
  20,
  102,
  125,
  246,
  18,
  97,
  246,
  158,
  220,
  184,
  15,
  209,
  105,
  212,
  8,
  115,
  61,
  255,
  61,
  199,
  255,
  255,
  255,
  0,
  140,
  69,
  18,
  0,
  102,
  0,
  255,
  61,
  150,
  237,
  216,
  173,
  132,
  114,
  0,
  114,
  246,
  255,
  158,
  193,
  255,
  114,
  200,
  123,
  158,
  0,
  0,
  0,
  79,
  255,
  0,
  70,
  149,
  211,
  255,
  0,
  185,
  79,
  211,
  62,
  0,
  26,
  237,
  255,
  176,
  255,
  123,
  97,
  70,
  255,
  123,
  18,
  167,
  97,
  211,
  167,
  167,
  211,
  79,
  132,
  106,
  0,
  193,
  44,
  97,
  70,
  0,
  149,
  246,
  9,
  62,
  79,
  167,
  88,
  9,
  114,
  150,
  62,
  9,
  149,
  0,
  158,
  50,
  185,
  255,
  255,
  114,
  167,
  246,
  202,
  149,
  100,
  185,
  176,
  176,
  9,
  44,
  0,
  79,
  0,
  202,
  255,
  79,
  35,
  0,
  0,
  185,
  167,
  158,
  0,
  53,
  79,
  123,
  176,
  26,
  70,
  193,
  88,
  211,
  0,
  114,
  149,
  53,
  229,
  167,
  53,
  246,
  141,
  149,
  106,
  18,
  141,
  229,
  97,
  193,
  237,
  211,
  255,
  158,
  255,
  158,
  255,
  79,
  246,
  149,
  97,
  255,
  193,
  185,
  97,
  237,
  44,
  53,
  176,
  106,
  132,
  70,
  44,
  88,
  185,
  132,
  88,
  141,
  9,
  255,
  70,
  79,
  0,
  220,
  211,
  176,
  229,
  149,
  255,
  0,
  0,
  9,
  97,
  62,
  62,
  237,
  0,
  132,
  176,
  0,
  149,
  106,
  0,
  0,
  185,
  167,
  255,
  167,
  50,
  193,
  132,
  44,
  79,
  114,
  50,
  114,
  220,
  70,
  0,
  9,
  53,
  0,
  149,
  202,
  123,
  0,
  18,
  211,
  185,
  237,
  255,
  88,
  62,
  149,
  70,
  132,
  70,
  176,
  255,
  79,
  70,
  202,
  88,
  229,
  88,
  106,
  237,
  141,
  70,
  123,
  79,
  18,
  97,
  114,
  202,
  0,
  255,
  237,
  79,
  149,
  132,
  185,
  0,
  202,
  255,
  220,
  114,
  18,
  35,
  185,
  167,
  0,
  106,
  158,
  114,
  106,
  26,
  185,
  202,
  0,
  44,
  35,
  149,
  250,
  79,
  176,
  211,
  9,
  185,
  70,
  53,
  0,
  114,
  255,
  255,
  246,
  0,
  97,
  167,
  202,
  88,
  211,
  158,
  114,
  70,
  193,
  141,
  202,
  185,
  18,
  53,
  97,
  132,
  255,
  0,
  70,
  106,
  123,
  0,
  35,
  132,
  70,
  0,
  62,
  176,
  79,
  149,
  193,
  79,
  255,
  211,
  255,
  141,
  9,
  26,
  88,
  62,
  70,
  62,
  97,
  88,
  149,
  246,
  167,
  141,
  246,
  123,
  255,
  229,
  255,
  220,
  255,
  176,
  202,
  123,
  70,
  114,
  185,
  123,
  255,
  97,
  158,
  0,
  255,
  176,
  0,
  44,
  18,
  9,
  88,
  255,
  79,
  211,
  202,
  211,
  176,
  123,
  18,
  132,
  53,
  9,
  44,
  132,
  35,
  106,
  18,
  35,
  114,
  62,
  255,
  44,
  35,
  53,
  70,
  176,
  246,
  53,
  229,
  141,
  255,
  229,
  158,
  246,
  132,
  176,
  97,
  114,
  62,
  149,
  62,
  158,
  193,
  211,
  88,
  176,
  158,
  132,
  211,
  114,
  97,
  141,
  246,
  114,
  202,
  158,
  79,
  0,
  211,
  62,
  185,
  123,
  176,
  255,
  97,
  176,
  202,
  35,
  62,
  132,
  149,
  220,
  35,
  0,
  35,
  158,
  1,
  149,
  70,
  106,
  0,
  202,
  18,
  97,
  0,
  97,
  97,
  0,
  132,
  97,
  79,
  62,
  9,
  97,
  176,
  79,
  255,
  26,
  255,
  0,
  88,
  132,
  0,
  97,
  202,
  211,
  97,
  44,
  211,
  35,
  149,
  141,
  44,
  193,
  185,
  220,
  167,
  88,
  35,
  106,
  229,
  220,
  44,
  255,
  202,
  185,
  132,
  141,
  0,
  255,
  88,
  141,
  237,
  185,
  255,
  211,
  176,
  0,
  132,
  211,
  255,
  114,
  255,
  211,
  79,
  9,
  158,
  79,
  88,
  237,
  97,
  176,
  132,
  141,
  106,
  211,
  193,
  211,
  246,
  62,
  70,
  35,
  220,
  70,
  202,
  176,
  255,
  246,
  220,
  255,
  88,
  158,
  18,
  229,
  193,
  88,
  106,
  202,
  176,
  123,
  123,
  1,
  167,
  18,
  114,
  193,
  255,
  97,
  62,
  202,
  114,
  0,
  114,
  70,
  44,
  123,
  193,
  0,
  193,
  220,
  211,
  176,
  149,
  18,
  97,
  44,
  70,
  211,
  123,
  202,
  255,
  79,
  97,
  79,
  141,
  255,
  158,
  18,
  79,
  70,
  70,
  88,
  97,
  229,
  211,
  246,
  246,
  141,
  167,
  97,
  53,
  0,
  141,
  193,
  255,
  0,
  211,
  141,
  79,
  88,
  185,
  106,
  211,
  211,
  158,
  193,
  0,
  97,
  44,
  255,
  211,
  229,
  149,
  18,
  26,
  106,
  211,
  123,
  53,
  9,
  114,
  123,
  211,
  220,
  0,
  26,
  44,
  220,
  0,
  9,
  158,
  123,
  53,
  114,
  106,
  35,
  202,
  132,
  123,
  158,
  9,
  167,
  255,
  106,
  0,
  255,
  141,
  237,
  193,
  255,
  185,
  193,
  211,
  132,
  229,
  141,
  97,
  62,
  44,
  167,
  97,
  167,
  176,
  158,
  1,
  158,
  132,
  132,
  97,
  255,
  123,
  53,
  88,
  88,
  193,
  79,
  193,
  44,
  141,
  176,
  0,
  193,
  185,
  246,
  255,
  220,
  79,
  114,
  97,
  97,
  114,
  44,
  26,
  70,
  9,
  0,
  132,
  70,
  141,
  106,
  62,
  211,
  132,
  158,
  255,
  97,
  0,
  106,
  158,
  53,
  0,
  123,
  246,
  176,
  53,
  88,
  35,
  0,
  167,
  123,
  141,
  1,
  158,
  185,
  141,
  246,
  88,
  132,
  97,
  35,
  9,
  62,
  158,
  185,
  88,
  202,
  62,
  35,
  176,
  141,
  211,
  211,
  193,
  70,
  123,
  1,
  176,
  88,
  132,
  132,
  106,
  9,
  70,
  167,
  62,
  114,
  70,
  53,
  35,
  88,
  35,
  255,
  0,
  158,
  62,
  0,
  62,
  35,
  0,
  220,
  246,
  211,
  0,
  193,
  255,
  176,
  97,
  18,
  158,
  158,
  0,
  220,
  185,
  70,
  88,
  123,
  246,
  246,
  193,
  229,
  97,
  255,
  211,
  141,
  158,
  53,
  88,
  149,
  141,
  167,
  97,
  149,
  176,
  211,
  26,
  35,
  35,
  255,
  176,
  176,
  132,
  0,
  132,
  88,
  106,
  79,
  176,
  255,
  18,
  9,
  35,
  0,
  53,
  53,
  123,
  255,
  193,
  237,
  106,
  167,
  97,
  255,
  26,
  79,
  114,
  123,
  26,
  158,
  149,
  53,
  255,
  229,
  220,
  141,
  211,
  158,
  0,
  220,
  0,
  149,
  53,
  70,
  62,
  26,
  44,
  0,
  132,
  62,
  185,
  255,
  114,
  220,
  237,
  35,
  0,
  114,
  149,
  0,
  0,
  88,
  246,
  202,
  158,
  202,
  106,
  158,
  255,
  132,
  202,
  185,
  1,
  202,
  0,
  255,
  149,
  202,
  70,
  149,
  158,
  35,
  132,
  185,
  97,
  246,
  255,
  70,
  26,
  237,
  149,
  35,
  141,
  62,
  237,
  193,
  123,
  53,
  114,
  158,
  211,
  141,
  97,
  0,
  114,
  255,
  149,
  158,
  53,
  53,
  97,
  9,
  220,
  132,
  246,
  79,
  44,
  62,
  255,
  132,
  106,
  237,
  193,
  53,
  88,
  149,
  220,
  114,
  149,
  132,
  220,
  79,
  132,
  0,
  193,
  79,
  185,
  123,
  176,
  255,
  114,
  53,
  53,
  185,
  193,
  141,
  229,
  193,
  114,
  149,
  79,
  193,
  255,
  123,
  123,
  211,
  158,
  132,
  220,
  255,
  255,
  0,
  220,
  158,
  26,
  193,
  106,
  9,
  0,
  176,
  123,
  0,
  53,
  0,
  62,
  158,
  9,
  114,
  97,
  35,
  35,
  70,
  158,
  176,
  53,
  255,
  0,
  44,
  88,
  62,
  88,
  255,
  26,
  123,
  255,
  88,
  211,
  229,
  88,
  79,
  0,
  211,
  211,
  193,
  0,
  26,
  176,
  229,
  70,
  62,
  97,
  88,
  167,
  106,
  79,
  158,
  1,
  149,
  193,
  237,
  149,
  123,
  176,
  62,
  70,
  44,
  220,
  229,
  62,
  255,
  246,
  114,
  141,
  220,
  18,
  176,
  211,
  97,
  0,
  255,
  53,
  167,
  18,
  70,
  70,
  185,
  237,
  211,
  114,
  53,
  149,
  211,
  114,
  141,
  0,
  158,
  193,
  79,
  106,
  229,
  97,
  193,
  167,
  79,
  70,
  114,
  141,
  229,
  9,
  220,
  53,
  132,
  35,
  26,
  106,
  211,
  158,
  246,
  70,
  141,
  211,
  0,
  88,
  106,
  149,
  123,
  79,
  132,
  114,
  97,
  211,
  229,
  79,
  106,
  193,
  70,
  255,
  167,
  255,
  220,
  158,
  106,
  35,
  0,
  18,
  211,
  106,
  70,
  149,
  229,
  149,
  18,
  18,
  0,
  123,
  1,
  158,
  158,
  88,
  141,
  141,
  97,
  114,
  255,
  149,
  132,
  70,
  53,
  62,
  79,
  132,
  158,
  9,
  0,
  149,
  44,
  62,
  0,
  141,
  18,
  88,
  53,
  106,
  35,
  202,
  185,
  176,
  106,
  79,
  246,
  149,
  255,
  229,
  79,
  35,
  35,
  9,
  0,
  70,
  53,
  35,
  0,
  97,
  88,
  70,
  0,
  246,
  255,
  0,
  220,
  106,
  220,
  114,
  220,
  114,
  229,
  255,
  97,
  35,
  88,
  132,
  70,
  35,
  193,
  35,
  0,
  114,
  97,
  185,
  167,
  79,
  106,
  185,
  106,
  114,
  88,
  62,
  123,
  79,
  44,
  149,
  88,
  97,
  97,
  202,
  88,
  88,
  97,
  79,
  0,
  35,
  53,
  79,
  79,
  193,
  229,
  237,
  229,
  255,
  220,
  141,
  185,
  149,
  1,
  141,
  255,
  193,
  53,
  211,
  9,
  79,
  132,
  26,
  0,
  132,
  220,
  62,
  35,
  62,
  202,
  149,
  202,
  229,
  53,
  149,
  97,
  185,
  62,
  220,
  44,
  158,
  132,
  132,
  53,
  106,
  176,
  0,
  255,
  97,
  18,
  53,
  88,
  229,
  97,
  220,
  211,
  149,
  220,
  149,
  62,
  211,
  123,
  246,
  88,
  88,
  35,
  229,
  220,
  132,
  18,
  0,
  26,
  132,
  114,
  185,
  158,
  70,
  44,
  44,
  79,
  0,
  185,
  220,
  114,
  141,
  97,
  53,
  185,
  237,
  35,
  149,
  193,
  158,
  0,
  53,
  114,
  176,
  132,
  141,
  106,
  141,
  70,
  220,
  0,
  220,
  141,
  44,
  167,
  246,
  193,
  97,
  255,
  246,
  220,
  255,
  88,
  79,
  255,
  255,
  79,
  220,
  185,
  211,
  106,
  53,
  0,
  176,
  79,
  35,
  79,
  132,
  44,
  53,
  123,
  220,
  176,
  132,
  106,
  0,
  185,
  53,
  220,
  185,
  158,
  202,
  167,
  220,
  9,
  0,
  229,
  237,
  229,
  106,
  149,
  18,
  202,
  106,
  114,
  149,
  158,
  149,
  255,
  88,
  185,
  114,
  123,
  1,
  123,
  35,
  53,
  35,
  70,
  88,
  167,
  255,
  132,
  79,
  44,
  44,
  97,
  44,
  176,
  229,
  53,
  114,
  70,
  132,
  9,
  35,
  88,
  176,
  167,
  53,
  79,
  88,
  114,
  0,
  158,
  149,
  53,
  255,
  97,
  123,
  193,
  114,
  149,
  9,
  123,
  88,
  158,
  237,
  193,
  70,
  229,
  255,
  123,
  193,
  53,
  158,
  79,
  35,
  123,
  255,
  229,
  185,
  0,
  185,
  0,
  0,
  123,
  123,
  123,
  88,
  79,
  211,
  149,
  211,
  51,
  102,
  153,
  128,
  1,
  179,
  0,
  255,
  255,
  35,
  35,
  9,
];

export const rgbs: Array<Vector3> = chunk3(rawRgbs).map(rgb =>
  // $FlowFixMe Flow has troubles with understanding that mapping a tuple, returns another tuple
  rgb.map(el => el / 255),
);

const ColorGenerator = {
  distinctColorForId(id: number): Vector3 {
    return rgbs[(id - 1 + rgbs.length) % rgbs.length];
  },

  getNRandomColors(n: number): Array<Vector3> {
    let shuffledColors = [];
    let remainingColorCount = n;

    while (remainingColorCount > 0) {
      // Take the first k colors
      const batchSize = Math.min(remainingColorCount, rgbs.length);
      shuffledColors = shuffledColors.concat(rgbs.slice(0, batchSize));
      remainingColorCount -= batchSize;
    }

    return _.shuffle(shuffledColors);
  },
};

export default ColorGenerator;
