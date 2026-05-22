# Xunfei Vendor Integration Matrix

This matrix records what can be verified without secrets, what must be verified in a credentialed environment, and where each capability is mounted in the agent platform.

| Capability | Provider | Business mount | Required env/config | Local check | Credentialed check |
| --- | --- | --- | --- | --- | --- |
| Long Text TTS | `XunfeiLongTextToSpeechProvider` | Career interview TTS plan and synthesis | `career.tts.xunfei.app-id`, `api-key`, `api-secret`, `submit-url`, `query-url` | Mock provider tests and static matrix | Submit async task, poll until success, download Base64 audio |
| XingChen Demeanor | `XunfeiXingChenDemeanorAnalysisProvider` | Career demeanor auxiliary endpoint | `career.demeanor.xunfei.*` workflow credentials | Mock provider tests and failure mapping | Upload image URL/base64, verify workflow score and signals |
| OCR | `XunfeiOcrProvider` | Resume image/PDF OCR fallback | `career.xunfei.ocr.*` | Resume OCR hook test | Submit image page, verify extracted text lines |
| FaceDetect | `XunfeiFaceDetectProvider` | Demeanor normalization signal model | `career.xunfei.face-detect.*` | Face signal normalization test | Submit image frame, verify face count/emotion/confidence |
| NLP | `XunfeiNlpProvider` | JD/resume/interview auxiliary keyword/entity/sentiment enrichment | `career.xunfei.nlp.*` | NLP enrichment test | Submit text sample, verify keywords/entities/sentiment |

## Static Acceptance

Run:

```powershell
python scripts/verify_post_fusion_acceptance.py
```

The static acceptance verifies provider classes, business hooks, sample response files, and the `failure-code-map` artifact.

## Credentialed Acceptance

Real-key acceptance must be executed only in an environment that has valid Xunfei credentials and network access. The local repository intentionally stores no secrets.

Minimum evidence to attach after credentialed runs:

- Request trace id and provider sid.
- Sanitized request payload shape without keys or image/audio content.
- Sanitized response sample.
- Failure code mapping if provider returns a non-success status.
- Business artifact id, such as resume document id, interview session id, or JD id.

## failure-code-map

The canonical local failure sample is `docs/examples/xunfei/failure-code-map.json`, and sanitized success response shapes are in `docs/examples/xunfei/sanitized-provider-samples.json`. Runtime code should map provider-specific failures into stable product-facing categories before surfacing them to controllers.
