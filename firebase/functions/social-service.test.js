const test = require("node:test");
const assert = require("node:assert/strict");
const { createSocialService } = require("./social-service");

function request(uid, token = {}, data = {}) {
  return {
    auth: {
      uid,
      token: {
        firebase: { sign_in_provider: "google.com" },
        ...token,
      },
    },
    data,
  };
}

test("report review requires a server-issued moderator or admin claim", async () => {
  const service = createSocialService({});

  await assert.rejects(
    service.reviewReport({ data: { reportId: "report-0001", status: "RESOLVED" } }),
    (error) => error.code === "unauthenticated"
  );

  for (const token of [{}, { moderator: false }, { admin: false }, { moderator: false, admin: false }]) {
    await assert.rejects(
      service.reviewReport(request("ordinary-player", token, {
        reportId: "report-0001",
        status: "RESOLVED",
      })),
      (error) => error.code === "permission-denied"
        && error.message === "Moderator authorization is required."
    );
  }
});

test("report review rejects unsupported fields and invalid statuses", async () => {
  const service = createSocialService({});
  await assert.rejects(
    service.reviewReport(request("moderator-player", { moderator: true }, {
      reportId: "report-0001",
      status: "RESOLVED",
      isAdmin: true,
    })),
    (error) => error.code === "invalid-argument"
  );
  await assert.rejects(
    service.reviewReport(request("moderator-player", { moderator: true }, {
      reportId: "report-0001",
      status: "DELETE_ACCOUNT",
    })),
    (error) => error.code === "invalid-argument"
  );
});
