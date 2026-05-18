import * as React from "react";
import { Link } from "react-router-dom";
import { LifeBuoy, MessageSquarePlus, Send, Sparkles } from "lucide-react";
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

  const currentTurn = turn || session?.currentQuestion || null;

  React.useEffect(() => {
    const nextTurnNo = currentTurn?.turnNo;
    if (nextTurnNo != null && !turnNo) {
      setTurnNo(String(nextTurnNo));
    }
  }, [currentTurn?.turnNo, turnNo]);

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
      toast.success("Question loaded.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load question."));
    } finally {
      setLoading(false);
    }
  };

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
        answerRevision: answerRevision.trim()
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
              <div className="grid gap-3 md:grid-cols-3">
                <Metric label="sessionStatus" value={session?.status || "-"} />
                <Metric label="currentTurnNo" value={session?.currentTurnNo ?? currentTurn?.turnNo ?? "-"} />
                <Metric label="turnStatus" value={currentTurn?.status || "-"} />
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
              <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-5">
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
