#!/usr/bin/env node
"use strict";
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// src/index.ts
var import_commander = require("commander");
var import_chalk2 = __toESM(require("chalk"));

// src/client.ts
var import_axios = __toESM(require("axios"));

// src/error.ts
var LofiConnectionError = class extends Error {
  constructor(url) {
    super(`actuator\uC5D0 \uC5F0\uACB0\uD560 \uC218 \uC5C6\uC5B4\uC694: ${url}`);
    this.name = "LofiConnectionError";
  }
};
var LofiNotFoundError = class extends Error {
  constructor(commitHash) {
    super(`\uCEE4\uBC0B \uB370\uC774\uD130\uAC00 \uC5C6\uC5B4\uC694: ${commitHash}`);
    this.name = "LofiNotFoundError";
  }
};
var LofiUnexpectedError = class extends Error {
  constructor(message) {
    super(`\uC608\uC0C1\uCE58 \uBABB\uD55C \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC5B4\uC694: ${message}`);
    this.name = "LofiUnexpectedError";
  }
};

// src/client.ts
var LofiClient = class {
  constructor(baseUrl) {
    this.baseUrl = baseUrl.replace(/\/$/, "");
  }
  async diff(base, head) {
    return this.request(
      () => import_axios.default.get(`${this.baseUrl}/actuator/lofiDiff`, {
        params: { base, head }
      })
    );
  }
  async snapshot(commitHash) {
    return this.request(
      () => import_axios.default.get(`${this.baseUrl}/actuator/lofi/${commitHash}`),
      commitHash
    );
  }
  async request(fn, commitHash) {
    try {
      const { data } = await fn();
      return data;
    } catch (err) {
      if (import_axios.default.isAxiosError(err)) {
        const axiosErr = err;
        if (!axiosErr.response) {
          throw new LofiConnectionError(this.baseUrl);
        }
        if (axiosErr.response.status === 404 && commitHash) {
          throw new LofiNotFoundError(commitHash);
        }
        throw new LofiUnexpectedError(
          `HTTP ${axiosErr.response.status}`
        );
      }
      throw new LofiUnexpectedError(err.message);
    }
  }
};

// src/render.ts
var import_chalk = __toESM(require("chalk"));
var LINE = "\u2500".repeat(60);
function renderDiff(result) {
  console.log();
  console.log(
    import_chalk.default.bold("\uBC30\uD3EC \uBE44\uAD50") + "  " + import_chalk.default.gray(result.baseCommit.slice(0, 7)) + " \u2192 " + import_chalk.default.white(result.headCommit.slice(0, 7))
  );
  console.log(import_chalk.default.gray(LINE));
  console.log(
    import_chalk.default.gray("  \uBA54\uC11C\uB4DC".padEnd(45)) + import_chalk.default.gray("\uC774\uC804".padStart(8)) + import_chalk.default.gray("\uC774\uD6C4".padStart(8)) + import_chalk.default.gray("\uBCC0\uD654".padStart(10))
  );
  console.log(import_chalk.default.gray(LINE));
  for (const d of result.diffs) {
    const signature = d.signature.padEnd(44);
    const base = `${d.baseMs.toFixed(0)}ms`.padStart(7);
    const head = `${d.headMs.toFixed(0)}ms`.padStart(7);
    const delta = `${d.deltaMs > 0 ? "+" : ""}${d.deltaMs.toFixed(0)}ms`.padStart(8);
    const arrow = d.deltaMs > 0 ? " \u25B2" : " \u2014";
    if (d.regressed) {
      console.log(import_chalk.default.red(`  ${signature} ${base}  \u2192  ${head}  ${delta}${arrow}`));
    } else {
      console.log(import_chalk.default.gray(`  ${signature} ${base}  \u2192  ${head}  ${delta}${arrow}`));
    }
  }
  console.log(import_chalk.default.gray(LINE));
  const regressions = result.diffs.filter((d) => d.regressed);
  if (regressions.length > 0) {
    console.log(import_chalk.default.red.bold(`  \uC131\uB2A5 \uC800\uD558 ${regressions.length}\uAC74 \uAC10\uC9C0\uB428`));
  } else {
    console.log(import_chalk.default.green.bold("  \uC131\uB2A5 \uC800\uD558 \uC5C6\uC74C"));
  }
  console.log();
}
function renderSnapshot(snapshot) {
  console.log();
  console.log(import_chalk.default.bold("\uC2A4\uB0C5\uC0F7") + "  " + import_chalk.default.gray(snapshot.commitHash.slice(0, 7)));
  console.log(import_chalk.default.gray(LINE));
  console.log(import_chalk.default.gray(`  \uBC30\uD3EC \uC2DC\uAC01: ${snapshot.deployedAt}`));
  console.log(import_chalk.default.gray(`  \uC218\uC9D1 \uBA54\uD2B8\uB9AD: ${snapshot.metrics.length}\uAC74`));
  if (snapshot.metrics.length > 0) {
    const avgByMethod = /* @__PURE__ */ new Map();
    for (const m of snapshot.metrics) {
      const key = `${m.className}.${m.methodName}()`;
      const curr = avgByMethod.get(key) ?? { total: 0, count: 0 };
      avgByMethod.set(key, { total: curr.total + m.elapsedMs, count: curr.count + 1 });
    }
    const sorted = [...avgByMethod.entries()].sort((a, b) => b[1].total / b[1].count - a[1].total / a[1].count);
    console.log();
    console.log(
      import_chalk.default.gray("  \uBA54\uC11C\uB4DC".padEnd(45)) + import_chalk.default.gray("\uD3C9\uADE0".padStart(8)) + import_chalk.default.gray("\uD638\uCD9C".padStart(6))
    );
    console.log(import_chalk.default.gray(LINE));
    for (const [sig, { total, count }] of sorted) {
      const avg = total / count;
      console.log(import_chalk.default.gray(
        `  ${sig.padEnd(44)} ${(avg.toFixed(0) + "ms").padStart(7)} ${String(count).padStart(4)}\uD68C`
      ));
    }
  }
  console.log();
}

// src/index.ts
var program = new import_commander.Command();
program.name("lofi").description("Method-level deploy diff for Spring Boot teams").version("0.1.0");
function handleError(err) {
  if (err instanceof LofiConnectionError) {
    console.error(import_chalk2.default.red(`
\uC5F0\uACB0 \uC2E4\uD328 \u2014 ${err.message}`));
    console.error(import_chalk2.default.gray("  --url \uC635\uC158\uC73C\uB85C actuator \uC8FC\uC18C\uB97C \uD655\uC778\uD574\uC8FC\uC138\uC694"));
  } else if (err instanceof LofiNotFoundError) {
    console.error(import_chalk2.default.red(`
\uB370\uC774\uD130 \uC5C6\uC74C \u2014 ${err.message}`));
    console.error(import_chalk2.default.gray("  \uCEE4\uBC0B \uD574\uC2DC\uAC00 \uC62C\uBC14\uB978\uC9C0, \uD574\uB2F9 \uBC30\uD3EC \uB370\uC774\uD130\uAC00 \uC218\uC9D1\uB410\uB294\uC9C0 \uD655\uC778\uD574\uC8FC\uC138\uC694"));
  } else if (err instanceof LofiUnexpectedError) {
    console.error(import_chalk2.default.red(`
\uC624\uB958 \u2014 ${err.message}`));
  } else {
    console.error(import_chalk2.default.red("\n\uC54C \uC218 \uC5C6\uB294 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC5B4\uC694"));
    console.error(err);
  }
  process.exit(1);
}
program.command("diff <range>").description("Compare method latency between two deploys").option("-u, --url <url>", "actuator base url", "http://localhost:8080").action(async (range, options) => {
  const [base, head] = range.split("..");
  if (!base || !head) {
    console.error(import_chalk2.default.red("\n\uC62C\uBC14\uB978 \uD615\uC2DD: lofi diff <base>..<head>"));
    console.error(import_chalk2.default.gray("  \uC608\uC2DC: lofi diff a3f9c1..d82e04"));
    process.exit(1);
  }
  try {
    const client = new LofiClient(options.url);
    const result = await client.diff(base, head);
    renderDiff(result);
  } catch (err) {
    handleError(err);
  }
});
program.command("snapshot <commitHash>").description("Show metrics for a specific deploy").option("-u, --url <url>", "actuator base url", "http://localhost:8080").action(async (commitHash, options) => {
  try {
    const client = new LofiClient(options.url);
    const snapshot = await client.snapshot(commitHash);
    renderSnapshot(snapshot);
  } catch (err) {
    handleError(err);
  }
});
program.parseAsync(process.argv);
