from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


CHECKS = {
    "P1 Dockerfile": ("Dockerfile", ["EXPOSE 9090", "actuator/health/readiness"]),
    "P1 CI": (".github/workflows/ci.yml", ["verify_post_fusion_acceptance.py", "delivery-files"]),
    "P1 Compose health": ("resources/docker/ragent-dev-stack.compose.yaml", ["/api/ragent/actuator/health/readiness"]),
    "P1 OpenAPI groups": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/career/config/OpenApiDocumentationConfig.java",
        ["career-user", "career-admin", "career-runtime", "career-export"],
    ),
    "P2 font strategy": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeRenderValidationResult.java",
        ["fontStrategy"],
    ),
    "P2 font manifest": (
        "bootstrap/src/main/resources/fonts/font-manifest.yaml",
        ["Noto Sans SC", "SIL Open Font License 1.1", "classpath:/fonts/NotoSansSC-Regular.ttf"],
    ),
    "P2 bundled font": (
        "bootstrap/src/main/resources/fonts/NotoSansSC-Regular.ttf",
        [],
    ),
    "P2 CJK render sample": (
        "bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeRenderPipelineTest.java",
        ["htmlPdfAndDocxRenderCjkTypographyAcceptanceSample", "bundledFontManifestAndRegularFontAreAvailableForPdfRegistration"],
    ),
    "P3 memory tests": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/ConversationMemoryBucket.java",
        ["HOT_CONTEXT", "SHORT_SUMMARY", "LONG_TERM_FACT", "KEY_EVIDENCE", "RISK_FLAG"],
    ),
    "P4 TTS contract": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/tts/CareerTextToSpeechService.java",
        ["TEXT_FALLBACK", "career:tts:cancel", "cacheKey"],
    ),
    "P4 TTS endpoint": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerInterviewController.java",
        ["/career/interviews/{sessionId}/tts/plan"],
    ),
    "P5 demeanor service": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/demeanor/CareerDemeanorAnalysisService.java",
        ["CONSENT_REQUIRED", "AUXILIARY_READY"],
    ),
    "P5 demeanor score boundary": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/demeanor/CareerDemeanorAnalysisResult.java",
        ["includedInScore"],
    ),
    "P5 demeanor endpoint": (
        "bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerInterviewController.java",
        ["/career/interviews/{sessionId}/demeanor/analyze"],
    ),
    "P6 acceptance matrix": (
        "docs/career-agent-platform/post-fusion-acceptance-matrix.md",
        ["P1", "P2", "P3", "P4", "P5", "P6"],
    ),
    "Final gap audit": (
        "docs/career-agent-platform/post-fusion-gap-audit.md",
        ["已引入", "仍未引入", "真实第三方 TTS", "真实神态/表情模型"],
    ),
}


def main() -> int:
    failures = []
    for name, (relative_path, expected_tokens) in CHECKS.items():
        path = ROOT / relative_path
        if not path.exists():
            failures.append(f"{name}: missing {relative_path}")
            continue
        content = path.read_text(encoding="utf-8", errors="ignore")
        missing = [token for token in expected_tokens if token not in content]
        if missing:
            failures.append(f"{name}: missing tokens {missing} in {relative_path}")

    if failures:
        print("Post-fusion acceptance check failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Post-fusion acceptance check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
