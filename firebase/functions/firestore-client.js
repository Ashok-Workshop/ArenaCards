const { FieldPath, FieldValue, Firestore } = require("@google-cloud/firestore");

const FIREBASE_PROJECT_ID = "arenacards-f3279";
const FIRESTORE_DATABASE_ID = "unoashok";
const FUNCTIONS_REGION = "us-central1";

function validateRuntimeProject(projectId) {
  if (projectId && projectId !== FIREBASE_PROJECT_ID) {
    throw new Error(
      `Cloud Functions project mismatch. Expected ${FIREBASE_PROJECT_ID} but found ${projectId}.`
    );
  }
}

function createFirestoreClient() {
  validateRuntimeProject(
    process.env.GCLOUD_PROJECT
      || process.env.GOOGLE_CLOUD_PROJECT
      || process.env.GCP_PROJECT
  );
  return new Firestore({
    projectId: FIREBASE_PROJECT_ID,
    databaseId: FIRESTORE_DATABASE_ID,
  });
}

module.exports = {
  FIREBASE_PROJECT_ID,
  FIRESTORE_DATABASE_ID,
  FUNCTIONS_REGION,
  FieldPath,
  FieldValue,
  createFirestoreClient,
  validateRuntimeProject,
};
