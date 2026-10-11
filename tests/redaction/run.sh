#!/usr/bin/env bash
# Runs every photo in tests/redaction/input through the real OCR, face detector and blur and
# writes tests/reports/redaction/index.html: original, what was detected, what got published.
#
#   tests/redaction/run.sh [--languages ukr+eng]
set -euo pipefail
cd "$(dirname "$0")"

LANGUAGES=${REDACTION_OCR_LANGUAGES:-ukr+eng}
while [ $# -gt 0 ]; do
  case "$1" in
    --languages) LANGUAGES=$2; shift 2 ;;
    -h|--help)   sed -n '2,5p' "$0"; exit 0 ;;
    *)           echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

TESSDATA=${TESSDATA_PREFIX:-$PWD/.tessdata}
mkdir -p "$TESSDATA"
for language in ${LANGUAGES//+/ }; do
  if [ ! -s "$TESSDATA/$language.traineddata" ]; then
    echo "==> download $language.traineddata"
    curl -fsSL -o "$TESSDATA/$language.traineddata" \
      "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$language.traineddata"
  fi
done

cd ../..
TESSDATA_PREFIX="$TESSDATA" REDACTION_OCR_LANGUAGES="$LANGUAGES" \
  ./mvnw -q -pl Telegram-Ingest test -Dtest=RedactionReportIT -Dsurefire.failIfNoSpecifiedTests=false
echo "Report: tests/reports/redaction/index.html"
