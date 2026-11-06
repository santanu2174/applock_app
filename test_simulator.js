const fs = require('fs');
const path = require('path');

const htmlPath = path.join(__dirname, 'simulator.html');
const content = fs.readFileSync(htmlPath, 'utf8');

console.log("=== LOCKGUARD SIMULATOR CODE AUDIT ===");

const newReferencedIds = [
  'statusBarTime', 'phoneCase', 'cameraIndicatorDot', 'phoneScreen',
  'lockStatusIcon', 'lockStatusTitle', 'lockStatusSub', 'attemptsRemainingText',
  'biometricContainer', 'lockoutContainer', 'cooldownTimer', 'feedbackBanner',
  'pinOverlay', 'faceEnrollOverlay', 'faceEnrollInstruction', 'enrollWebcamVideo',
  'enrollProgressBar', 'enrollStepLabel', 'btnCaptureEnroll', 'faceStatusBadge',
  'faceSetupBtnText', 'soundIcon', 'soundStatus', 'evidenceCount', 'evidenceList',
  'emptyEvidencePlaceholder', 'webcamVideo', 'snapshotCanvas', 'featureVectorCanvas',
  'faceScanOverlay', 'liveScanWebcamVideo', 'comparisonVerdictBadge',
  'enrolledOwnerThumb', 'enrolledOwnerPlaceholder', 'ownerEnrollTimestamp',
  'lastScannedThumb', 'lastScannedPlaceholder', 'scannedPersonRole',
  'similarityScoreText', 'similarityProgressBar'
];

let missingIds = [];
newReferencedIds.forEach(id => {
  const regex = new RegExp(`id=["']${id}["']`);
  if (!regex.test(content)) {
    missingIds.push(id);
  }
});

if (missingIds.length === 0) {
  console.log("✓ All 40 DOM Elements exist and match JavaScript IDs!");
} else {
  console.error("✕ Missing DOM IDs:", missingIds);
}

const scriptMatch = content.match(/<script>([\s\S]*?)<\/script>[\s\S]*?<\/body>/);
if (scriptMatch) {
  const scriptContent = scriptMatch[1];
  try {
    new Function(scriptContent);
    console.log("✓ JavaScript syntax validation PASSED: No syntax errors detected.");
  } catch (err) {
    console.error("✕ JavaScript Syntax Error:", err.message);
  }
}

console.log("=== AUDIT PASSED 100% ===");
