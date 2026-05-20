import * as React from "react";
import { Link } from "react-router-dom";
import { LifeBuoy, Mic, MicOff, MessageSquarePlus, Radio, Send, Sparkles } from "lucide-react";
import { toast } from "sonner";

import { MainLayout } from "@/components/layout/MainLayout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  type CareerInterviewSession,
  type CareerInterviewTurn,
  type CareerProgressEvent,
  createCareerInterviewProgressStream,
  createCareerInterviewTranscriptionUrl,
  createCareerInterview,
  getCareerInterview,
  getCareerInterviewNextQuestion,
  recoverCareerInterview,
  submitCareerInterviewAnswer
} from "@/services/careerService";
import { getErrorMessage } from "@/utils/error";

export function CareerInterviewPage() {
  const [createForm, setCreateForm] = React.useState({ resumeVersionId: "", jdId: "" });
  const [sessionId, setSessionId] = React.useState("");
  const [session, setSession] = React.useState<CareerInterviewSession | null>(null);
  const [turn, setTurn] = React.useState<CareerInterviewTurn | null>(null);
  const [answer, setAnswer] = React.useState("");
  const [answerRevision, setAnswerRevision] = React.useState("rev-1");
  const [turnNo, setTurnNo] = React.useState("");
  const [loading, setLoading] = React.useState(false);
  const [creating, setCreating] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [streamConnected, setStreamConnected] = React.useState(false);
  const [lastProgress, setLastProgress] = React.useState<CareerProgressEvent | null>(null);
  const [recording, setRecording] = React.useState(false);
  const [asrStatus, setAsrStatus] = React.useState("idle");
  const [answerSource, setAnswerSource] = React.useState<"TEXT" | "ASR">("TEXT");
  const [answerSourceMeta, setAnswerSourceMeta] = React.useState<Record<string, unknown> | null>(null);
  const audioStreamRef = React.useRef<MediaStream | null>(null);
  const audioContextRef = React.useRef<AudioContext | null>(null);
  const audioSourceRef = React.useRef<MediaStreamAudioSourceNode | null>(null);
  const audioProcessorRef = React.useRef<ScriptProcessorNode | null>(null);
  const transcriptionSocketRef = React.useRef<WebSocket | null>(null);
  const recordingStartedAtRef = React.useRef<number | null>(null);

  const currentTurn = turn || session?.currentQuestion || null;

  React.useEffect(() => {
    const nextTurnNo = currentTurn?.turnNo;
    if (nextTurnNo != null && !turnNo) {
      setTurnNo(String(nextTurnNo));
    }
  }, [currentTurn?.turnNo, turnNo]);

  const applyInterviewProgress = React.useCallback((event: CareerProgressEvent) => {
    setLastProgress(event);
    const payload = event.payload;
    if (isInterviewSessionPayload(payload)) {
      setSession(payload);
      setTurn(payload.currentQuestion || null);
      setSessionId(payload.id);
      return;
    }
    if (isInterviewTurnPayload(payload)) {
      setTurn(payload);
      if (payload.turnNo != null) {
        setTurnNo(String(payload.turnNo));
      }
    }
  }, []);

  const refreshSessionSilently = React.useCallback(async (id: string) => {
    try {
      const result = await getCareerInterview(id);
      setSession(result);
      setTurn(result.currentQuestion || null);
      setSessionId(result.id || id);
    } catch (error) {
      console.warn("Career interview progress fallback reload failed", error);
    }
  }, []);

  React.useEffect(() => {
    const id = session?.id;
    if (!id || session?.status === "COMPLETED" || session?.status === "CANCELLED") {
      return;
    }
    let active = true;
    const stream = createCareerInterviewProgressStream(id, {
      onConnected: () => {
        if (active) {
          setStreamConnected(true);
        }
      },
      onProgress: (event) => {
        if (active) {
          applyInterviewProgress(event);
        }
      },
      onDone: () => {
        if (active) {
          setStreamConnected(false);
          void refreshSessionSilently(id);
        }
      },
      onError: (error) => {
        if (active) {
          setStreamConnected(false);
          console.warn("Career interview progress stream failed", error);
        }
      }
    });

    stream.start().catch((error) => {
      if (active && (error as Error).name !== "AbortError") {
        setStreamConnected(false);
        console.warn("Career interview progress stream closed", error);
      }
    });

    return () => {
      active = false;
      setStreamConnected(false);
      stream.cancel();
    };
  }, [applyInterviewProgress, refreshSessionSilently, session?.id, session?.status]);

  const loadSession = async (nextSessionId = sessionId) => {
    const id = nextSessionId.trim();
    if (!id) {
      toast.error("Enter sessionId.");
      return;
    }
    setLoading(true);
    try {
      const result = await getCareerInterview(id);
      setSession(result);
      setTurn(result.currentQuestion || null);
      setSessionId(result.id || id);
      setAnswer(result.currentQuestion?.answer || "");
      setAnswerSource(result.currentQuestion?.answerSource === "ASR" ? "ASR" : "TEXT");
      setAnswerSourceMeta(result.currentQuestion?.answerSourceMeta || null);
      toast.success("Interview session loaded.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load interview session."));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!createForm.resumeVersionId.trim() || !createForm.jdId.trim()) {
      toast.error("Enter resumeVersionId and jdId.");
      return;
    }
    setCreating(true);
    try {
      const result = await createCareerInterview(createForm.resumeVersionId.trim(), createForm.jdId.trim());
      setSession(result);
      setTurn(result.currentQuestion || null);
      setSessionId(result.id);
      setAnswer("");
      setAnswerSource("TEXT");
      setAnswerSourceMeta(null);
      toast.success("Interview session created.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to create interview."));
    } finally {
      setCreating(false);
    }
  };

  const handleNextQuestion = async () => {
    const id = sessionId.trim();
    if (!id) {
      toast.error("Enter sessionId.");
      return;
    }
    setLoading(true);
    try {
      const result = await getCareerInterviewNextQuestion(id);
      setTurn(result);
      setTurnNo(result.turnNo == null ? "" : String(result.turnNo));
      setAnswer(result.answer || "");
      setAnswerSource("TEXT");
      setAnswerSourceMeta(null);
      toast.success("Question loaded.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load question."));
    } finally {
      setLoading(false);
    }
  };

  const releaseAudioResources = React.useCallback(() => {
    audioProcessorRef.current?.disconnect();
    audioSourceRef.current?.disconnect();
    const audioContext = audioContextRef.current;
    audioProcessorRef.current = null;
    audioSourceRef.current = null;
    audioContextRef.current = null;
    if (audioContext && audioContext.state !== "closed") {
      void audioContext.close();
    }
    audioStreamRef.current?.getTracks().forEach((track) => track.stop());
    audioStreamRef.current = null;
  }, []);

  const closeTranscriptionSocket = React.useCallback(() => {
    const socket = transcriptionSocketRef.current;
    transcriptionSocketRef.current = null;
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      socket.close();
    }
  }, []);

  const startPcmStreaming = React.useCallback((socket: WebSocket) => {
    const stream = audioStreamRef.current;
    if (!stream) {
      toast.error("Microphone stream is unavailable.");
      return;
    }
    const AudioContextCtor = window.AudioContext || (window as AudioWindow).webkitAudioContext;
    if (!AudioContextCtor) {
      toast.error("Current browser does not support recording.");
      return;
    }
    const audioContext = new AudioContextCtor();
    const source = audioContext.createMediaStreamSource(stream);
    const processor = audioContext.createScriptProcessor(4096, 1, 1);
    recordingStartedAtRef.current = Date.now();
    processor.onaudioprocess = (event) => {
      if (socket.readyState !== WebSocket.OPEN) {
        return;
      }
      const input = event.inputBuffer.getChannelData(0);
      const pcm = downsampleToPcm16(input, audioContext.sampleRate, 16000);
      if (pcm.byteLength > 0) {
        socket.send(pcm);
      }
    };
    source.connect(processor);
    processor.connect(audioContext.destination);
    audioContextRef.current = audioContext;
    audioSourceRef.current = source;
    audioProcessorRef.current = processor;
    setRecording(true);
    setAsrStatus("recording");
  }, []);

  const applyTranscriptionDraft = React.useCallback((message: TranscriptionMessage) => {
    const update = extractTranscriptionUpdate(message);
    const text = readString(update.displayText) || readString(update.fullText) || readString(update.data);
    if (!text) {
      return;
    }
    const revision = update.revision ?? message.revision ?? Date.now();
    const durationMs = recordingStartedAtRef.current == null ? undefined : Date.now() - recordingStartedAtRef.current;
    setAnswer(text);
    setAnswerRevision(`asr-${revision}`);
    setAnswerSource("ASR");
    setAnswerSourceMeta({
      revision,
      resultStatus: update.resultStatus ?? message.resultStatus ?? message.type,
      segmentId: update.segmentId ?? message.segmentId,
      sentenceSeq: update.sentenceSeq ?? message.sentenceSeq,
      pgs: update.pgs ?? message.pgs,
      rg: update.rg ?? message.rg,
      bg: update.bg ?? message.bg,
      ed: update.ed ?? message.ed,
      isFinalPacket: update.isFinalPacket ?? message.isFinalPacket ?? message.type === "final",
      durationMs,
      mimeType: "audio/pcm;rate=16000"
    });
  }, []);

  const handleTranscriptionMessage = React.useCallback((event: MessageEvent<string>) => {
    let message: TranscriptionMessage;
    try {
      message = JSON.parse(event.data) as TranscriptionMessage;
    } catch {
      return;
    }
    if (message.type === "transcription_started") {
      const socket = transcriptionSocketRef.current;
      if (socket) {
        startPcmStreaming(socket);
      }
      return;
    }
    if (message.type === "transcription" || message.type === "final" || message.type === "transcription_stopped") {
      applyTranscriptionDraft(message);
      setAsrStatus(message.type === "final" ? "final" : "draft");
      return;
    }
    if (message.type === "error") {
      setAsrStatus("error");
      toast.error(readString(message.message) || "Transcription failed.");
      releaseAudioResources();
    }
  }, [applyTranscriptionDraft, releaseAudioResources, startPcmStreaming]);

  const handleStartRecording = async () => {
    const id = sessionId.trim();
    if (!id) {
      toast.error("Load or create an interview session first.");
      return;
    }
    if (!(window.AudioContext || (window as AudioWindow).webkitAudioContext) || !navigator.mediaDevices?.getUserMedia) {
      toast.error("Current browser does not support recording.");
      return;
    }
    try {
      closeTranscriptionSocket();
      releaseAudioResources();
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      audioStreamRef.current = stream;
      const socket = new WebSocket(createCareerInterviewTranscriptionUrl(id));
      transcriptionSocketRef.current = socket;
      setAsrStatus("connecting");
      socket.onopen = () => {
        socket.send(JSON.stringify({ type: "start_transcription" }));
      };
      socket.onmessage = (event) => handleTranscriptionMessage(event as MessageEvent<string>);
      socket.onerror = () => {
        setAsrStatus("error");
        toast.error("Transcription channel failed.");
        releaseAudioResources();
      };
      socket.onclose = () => {
        if (transcriptionSocketRef.current === socket) {
          transcriptionSocketRef.current = null;
        }
        setRecording(false);
        if (asrStatus === "recording") {
          setAsrStatus("closed");
        }
      };
    } catch (error) {
      releaseAudioResources();
      toast.error(getErrorMessage(error, "Failed to start recording."));
    }
  };

  const handleStopRecording = () => {
    releaseAudioResources();
    setRecording(false);
    setAsrStatus("stopping");
    const socket = transcriptionSocketRef.current;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: "stop_transcription" }));
    }
  };

  React.useEffect(() => {
    return () => {
      releaseAudioResources();
      closeTranscriptionSocket();
    };
  }, [closeTranscriptionSocket, releaseAudioResources]);

  const handleSubmitAnswer = async () => {
    const id = sessionId.trim();
    if (!id) {
      toast.error("Enter sessionId.");
      return;
    }
    if (!answer.trim()) {
      toast.error("Enter an answer.");
      return;
    }
    setSubmitting(true);
    try {
      const result = await submitCareerInterviewAnswer(id, {
        turnNo: turnNo.trim() ? Number(turnNo) : currentTurn?.turnNo ?? undefined,
        answer: answer.trim(),
        answerRevision: answerRevision.trim(),
        answerSource,
        answerSourceMeta: answerSource === "ASR" && answerSourceMeta ? answerSourceMeta : undefined
      });
      setTurn(result);
      toast.success("Answer submitted.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to submit answer."));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRecover = async () => {
    const id = sessionId.trim();
    if (!id) {
      toast.error("Enter sessionId.");
      return;
    }
    setLoading(true);
    try {
      const result = await recoverCareerInterview(id);
      setSession(result);
      setTurn(result.currentQuestion || null);
      toast.success("Session recovered.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to recover session."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <MainLayout>
      <div className="h-full overflow-y-auto bg-[#F8FAFC]">
        <div className="mx-auto flex max-w-6xl flex-col gap-5 px-6 py-6">
          <div className="flex flex-col gap-3 rounded-lg border bg-white p-5 shadow-sm lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-primary" />
                <h1 className="text-2xl font-semibold text-slate-950">AI Interview</h1>
              </div>
              <p className="mt-2 text-sm text-slate-600">
                Query a session, fetch questions, submit answers, and inspect idempotency and recovery state.
              </p>
            </div>
            <Button asChild variant="outline">
              <Link to="/career">Back To Career</Link>
            </Button>
          </div>

          <div className="grid gap-4 lg:grid-cols-[380px_1fr]">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Session Control</CardTitle>
                <CardDescription>Create a session or continue with an existing sessionId.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-3">
                  <Input
                    value={createForm.resumeVersionId}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, resumeVersionId: event.target.value }))}
                    placeholder="resumeVersionId"
                  />
                  <Input
                    value={createForm.jdId}
                    onChange={(event) => setCreateForm((prev) => ({ ...prev, jdId: event.target.value }))}
                    placeholder="jdId"
                  />
                  <Button onClick={handleCreate} disabled={creating}>
                    <MessageSquarePlus className="h-4 w-4" />
                    {creating ? "Creating..." : "Create Interview"}
                  </Button>
                </div>
                <div className="space-y-2">
                  <Label>sessionId</Label>
                  <div className="flex gap-2">
                    <Input value={sessionId} onChange={(event) => setSessionId(event.target.value)} placeholder="sessionId" />
                    <Button variant="outline" onClick={() => loadSession()} disabled={loading}>
                      Load
                    </Button>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" onClick={handleNextQuestion} disabled={loading}>
                    Next Question
                  </Button>
                  <Button variant="outline" onClick={handleRecover} disabled={loading}>
                    <LifeBuoy className="h-4 w-4" />
                    Recover
                  </Button>
                </div>
              </CardContent>
            </Card>

            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-4">
                <Metric label="sessionStatus" value={session?.status || "-"} />
                <Metric label="currentTurnNo" value={session?.currentTurnNo ?? currentTurn?.turnNo ?? "-"} />
                <Metric label="turnStatus" value={currentTurn?.status || "-"} />
                <Metric label="progressStream" value={streamConnected ? "connected" : lastProgress?.eventType || "-"} />
              </div>

              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Current Question</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="rounded-lg border bg-white p-4">
                    <div className="flex flex-wrap gap-2">
                      <Badge variant="secondary">{currentTurn?.turnType || "QUESTION"}</Badge>
                      <Badge variant="outline">turnNo {currentTurn?.turnNo ?? "-"}</Badge>
                      {currentTurn?.score != null ? <Badge>score {currentTurn.score}</Badge> : null}
                    </div>
                    <p className="mt-3 whitespace-pre-wrap text-sm text-slate-800">
                      {currentTurn?.question || "No question yet. Create a session or click Next Question."}
                    </p>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-[140px_1fr]">
                    <Input value={turnNo} onChange={(event) => setTurnNo(event.target.value)} placeholder="turnNo" />
                    <Input
                      value={answerRevision}
                      onChange={(event) => setAnswerRevision(event.target.value)}
                      placeholder="answerRevision"
                    />
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border bg-slate-50 p-3">
                    <div className="flex items-center gap-2 text-sm text-slate-600">
                      <Radio className="h-4 w-4" />
                      <span>ASR {asrStatus}</span>
                      {answerSource === "ASR" ? <Badge variant="secondary">ASR draft</Badge> : <Badge variant="outline">TEXT</Badge>}
                    </div>
                    <div className="flex gap-2">
                      <Button variant="outline" size="sm" onClick={handleStartRecording} disabled={recording || submitting}>
                        <Mic className="h-4 w-4" />
                        Start
                      </Button>
                      <Button variant="outline" size="sm" onClick={handleStopRecording} disabled={!recording}>
                        <MicOff className="h-4 w-4" />
                        Stop
                      </Button>
                    </div>
                  </div>
                  <Textarea value={answer} onChange={(event) => setAnswer(event.target.value)} placeholder="Answer" rows={5} />
                  <Button onClick={handleSubmitAnswer} disabled={submitting}>
                    <Send className="h-4 w-4" />
                    {submitting ? "Submitting..." : "Submit Answer"}
                  </Button>
                </CardContent>
              </Card>
            </div>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Idempotency And Compensation State</CardTitle>
              <CardDescription>Runtime fields from the AI-Meeting style turn state machine.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-6">
                <StatusTile label="answerSource" value={currentTurn?.answerSource || answerSource} />
                <StatusTile label="stepIdempotencyKey" value={currentTurn?.stepIdempotencyKey} />
                <StatusTile label="answerStatus" value={currentTurn?.answerStatus} />
                <StatusTile label="evaluationStatus" value={currentTurn?.evaluationStatus} />
                <StatusTile label="followUpDecisionStatus" value={currentTurn?.followUpDecisionStatus} />
                <StatusTile label="compensationStatus" value={currentTurn?.compensationStatus} />
              </div>
              {currentTurn?.lastError ? (
                <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
                  {currentTurn.lastError}
                </div>
              ) : null}
              {currentTurn?.feedback ? (
                <pre className="mt-4 overflow-auto rounded-lg bg-slate-950 p-3 text-xs text-slate-100">
                  {JSON.stringify(currentTurn.feedback, null, 2)}
                </pre>
              ) : null}
            </CardContent>
          </Card>
        </div>
      </div>
    </MainLayout>
  );
}

function Metric({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-xs text-slate-500">{label}</p>
        <p className="mt-2 break-all text-lg font-semibold text-slate-950">{value}</p>
      </CardContent>
    </Card>
  );
}

function StatusTile({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="rounded-lg border bg-white p-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-2 break-all text-sm font-medium text-slate-900">{value || "-"}</p>
    </div>
  );
}

type TranscriptionMessage = Record<string, unknown> & {
  type?: string;
  message?: string;
  data?: unknown;
  fullText?: unknown;
  displayText?: unknown;
  revision?: unknown;
  resultStatus?: unknown;
  segmentId?: unknown;
  sentenceSeq?: unknown;
  pgs?: unknown;
  rg?: unknown;
  bg?: unknown;
  ed?: unknown;
  isFinalPacket?: unknown;
};

type AudioWindow = Window & typeof globalThis & {
  webkitAudioContext?: typeof AudioContext;
};

function isInterviewSessionPayload(payload: unknown): payload is CareerInterviewSession {
  return isRecord(payload) && typeof payload.id === "string" && ("currentQuestion" in payload || "currentTurnNo" in payload);
}

function isInterviewTurnPayload(payload: unknown): payload is CareerInterviewTurn {
  return isRecord(payload) && ("turnNo" in payload || "question" in payload) && !("currentQuestion" in payload);
}

function extractTranscriptionUpdate(message: TranscriptionMessage): TranscriptionMessage {
  return isRecord(message.data) ? (message.data as TranscriptionMessage) : message;
}

function readString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object";
}

function downsampleToPcm16(input: Float32Array, sourceRate: number, targetRate: number): ArrayBuffer {
  if (!input.length || sourceRate <= 0 || targetRate <= 0) {
    return new ArrayBuffer(0);
  }
  const ratio = sourceRate / targetRate;
  const outputLength = Math.max(1, Math.floor(input.length / ratio));
  const output = new Int16Array(outputLength);
  for (let index = 0; index < outputLength; index += 1) {
    const start = Math.floor(index * ratio);
    const end = Math.min(input.length, Math.floor((index + 1) * ratio));
    let sum = 0;
    let count = 0;
    for (let cursor = start; cursor < end; cursor += 1) {
      sum += input[cursor];
      count += 1;
    }
    const sample = Math.max(-1, Math.min(1, count === 0 ? input[start] || 0 : sum / count));
    output[index] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
  }
  return output.buffer;
}
