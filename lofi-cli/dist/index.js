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
    super(`Cannot connect to actuator: ${url}`);
    this.name = "LofiConnectionError";
  }
};
var LofiNotFoundError = class extends Error {
  constructor(commitHash) {
    super(`No data found for commit: ${commitHash}`);
    this.name = "LofiNotFoundError";
  }
};
var LofiUnexpectedError = class extends Error {
  constructor(message) {
    super(`Unexpected error: ${message}`);
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
var DIFF_LINE = "\u2500".repeat(78);
var SNAPSHOT_LINE = "\u2500".repeat(60);
function shortSignature(signature) {
  const parts = signature.split(".");
  const methodPart = parts[parts.length - 1];
  const className = parts[parts.length - 2];
  return `${className}.${methodPart}`;
}
function renderDiff(result) {
  console.log();
  console.log(
    import_chalk.default.bold("Deploy Diff") + "  " + import_chalk.default.gray(result.baseCommit) + " \u2192 " + import_chalk.default.white(result.headCommit)
  );
  console.log(import_chalk.default.gray(DIFF_LINE));
  console.log(
    import_chalk.default.gray("  Method".padEnd(47)) + import_chalk.default.gray("Before".padStart(7)) + import_chalk.default.gray("     ") + import_chalk.default.gray("After".padStart(7)) + import_chalk.default.gray("  ") + import_chalk.default.gray("Delta".padStart(10))
  );
  console.log(import_chalk.default.gray(DIFF_LINE));
  for (const d of result.diffs) {
    const signature = shortSignature(d.signature).padEnd(44);
    const base = `${d.baseMs.toFixed(2)}ms`.padStart(9);
    const head = `${d.headMs.toFixed(2)}ms`.padStart(9);
    const delta = `${d.deltaMs > 0 ? "+" : ""}${d.deltaMs.toFixed(2)}ms`.padStart(10);
    const arrow = d.deltaMs > 0 ? " \u25B2" : " \u2014";
    if (d.regressed) {
      console.log(import_chalk.default.red(`  ${signature} ${base}  \u2192  ${head}  ${delta}${arrow}`));
    } else {
      console.log(import_chalk.default.gray(`  ${signature} ${base}  \u2192  ${head}  ${delta}${arrow}`));
    }
  }
  console.log(import_chalk.default.gray(DIFF_LINE));
  const regressions = result.diffs.filter((d) => d.regressed);
  if (regressions.length > 0) {
    console.log(import_chalk.default.red.bold(`  ${regressions.length} regression(s) detected`));
  } else {
    console.log(import_chalk.default.green.bold("  No regressions detected"));
  }
  console.log();
}
function renderSnapshot(snapshot) {
  console.log();
  console.log(import_chalk.default.bold("Snapshot") + "  " + import_chalk.default.gray(snapshot.commitHash));
  console.log(import_chalk.default.gray(SNAPSHOT_LINE));
  console.log(import_chalk.default.gray(`  Deployed at: ${snapshot.deployedAt}`));
  console.log(import_chalk.default.gray(`  Metrics collected: ${snapshot.metrics.length}`));
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
      import_chalk.default.gray("  Method".padEnd(45)) + import_chalk.default.gray("Avg".padStart(9)) + import_chalk.default.gray("Calls".padStart(7))
    );
    console.log(import_chalk.default.gray(SNAPSHOT_LINE));
    for (const [sig, { total, count }] of sorted) {
      const avgMs = total / count;
      console.log(import_chalk.default.gray(
        `  ${shortSignature(sig).padEnd(44)} ${(avgMs.toFixed(2) + "ms").padStart(8)} ${String(count).padStart(5)}`
      ));
    }
  }
  console.log();
}

// src/index.ts
var program = new import_commander.Command();
program.name("lofi").description("Method-level deploy diff for Spring Boot teams").version("0.1.7");
function handleError(err) {
  if (err instanceof LofiConnectionError) {
    console.error(import_chalk2.default.red(`
Connection failed \u2014 ${err.message}`));
    console.error(import_chalk2.default.gray("  Check the actuator URL with the --url option"));
  } else if (err instanceof LofiNotFoundError) {
    console.error(import_chalk2.default.red(`
No data found \u2014 ${err.message}`));
    console.error(import_chalk2.default.gray("  Verify the commit hash is correct and that metrics were collected for that deploy"));
  } else if (err instanceof LofiUnexpectedError) {
    console.error(import_chalk2.default.red(`
Error \u2014 ${err.message}`));
  } else {
    console.error(import_chalk2.default.red("\nAn unknown error occurred"));
    console.error(err);
  }
  process.exit(1);
}
program.command("diff <range>").description("Compare method latency between two deploys").option("-u, --url <url>", "actuator base url", "http://localhost:8080").action(async (range, options) => {
  const [base, head] = range.split("..");
  if (!base || !head) {
    console.error(import_chalk2.default.red("\nInvalid format: lofi diff <base>..<head>"));
    console.error(import_chalk2.default.gray("  Example: lofi diff a3f9c1..d82e04"));
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
