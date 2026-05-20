import * as React from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Check, RefreshCcw, RotateCw, WandSparkles, X } from "lucide-react";
import { toast } from "sonner";

import { MainLayout } from "@/components/layout/MainLayout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  type CareerProgressEvent,
  type CareerOptimizationSuggestion,
  type CareerOptimizationTask,
  type CareerResumeVersion,
  createCareerOptimization,
  createCareerOptimizationProgressStream,
  decideCareerOptimizationSuggestion,
  generateCareerOptimizationVersion,
  getCareerOptimization
} from "@/services/careerService";
import { getErrorMessage } from "@/utils/error";

export function CareerOptimizationPage() {
  const [searchParams] = useSearchParams();
  const [createForm, setCreateForm] = React.useState({
    resumeVersionId: "",
    jdId: "",
    alignmentReportId: searchParams.get("alignmentReportId") || ""
  });
  const [taskId, setTaskId] = React.useState("");
  const [task, setTask] = React.useState<CareerOptimizationTask | null>(null);
  const [version, setVersion] = React.useState<CareerResumeVersion | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [creating, setCreating] = React.useState(false);
  const [generating, setGenerating] = React.useState(false);
  const [editedText, setEditedText] = React.useState<Record<string, string>>({});

  const loadTask = async (nextTaskId = taskId) => {
    const id = nextTaskId.trim();
    if (!id) {
      toast.error("Enter taskId.");
      return;
    }
    setLoading(true);
    try {
      const result = await getCareerOptimization(id);
      setTask((prev) => mergeTaskProgress(prev, result));
      setTaskId(result.id || id);
      toast.success("Optimization task loaded.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load optimization task."));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!createForm.resumeVersionId.trim()) {
      toast.error("Enter resumeVersionId.");
      return;
    }
    setCreating(true);
    try {
      const result = await createCareerOptimization({
        resumeVersionId: createForm.resumeVersionId.trim(),
        jdId: createForm.jdId.trim() || undefined,
        alignmentReportId: createForm.alignmentReportId.trim() || undefined
      });
      setTask((prev) => mergeTaskProgress(prev, result));
      setTaskId(result.id);
      toast.success("Optimization task created.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to create optimization task."));
    } finally {
      setCreating(false);
    }
  };

  const handleDecision = async (suggestion: CareerOptimizationSuggestion, status: string) => {
    try {
      const updated = await decideCareerOptimizationSuggestion(suggestion.id, status, editedText[suggestion.id]);
      setTask((prev) =>
        prev
          ? {
              ...prev,
              suggestions: (prev.suggestions || []).map((item) => (item.id === updated.id ? updated : item))
            }
          : prev
      );
      toast.success("Suggestion updated.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to update suggestion."));
    }
  };

  const handleGenerateVersion = async () => {
    const id = task?.id || taskId.trim();
    if (!id) {
      toast.error("Load an optimization task first.");
      return;
    }
    setGenerating(true);
    try {
      const result = await generateCareerOptimizationVersion(id);
      setVersion(result);
      toast.success("Resume version generated.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to generate resume version."));
    } finally {
      setGenerating(false);
    }
  };

  const refreshTaskSilently = React.useCallback(async (id: string) => {
    try {
      const result = await getCareerOptimization(id);
      setTask((prev) => mergeTaskProgress(prev, result));
      setTaskId(result.id || id);
    } catch (error) {
      console.warn("Career progress fallback reload failed", error);
    }
  }, []);

  React.useEffect(() => {
    const id = task?.id;
    if (!id || task.status !== "RUNNING") {
      return;
    }

    let active = true;
    const stream = createCareerOptimizationProgressStream(id, {
      onProgress: (event) => {
        if (!active) {
          return;
        }
        setTask((prev) =>
          prev && prev.id === id
            ? {
                ...prev,
                progressEvents: mergeProgressEvents(prev.progressEvents, [event])
              }
            : prev
        );
      },
      onDone: () => {
        if (active) {
          void refreshTaskSilently(id);
        }
      },
      onError: (error) => {
        if (active) {
          console.warn("Career progress stream failed", error);
          void refreshTaskSilently(id);
        }
      }
    });

    stream.start().catch((error) => {
      if (active && (error as Error).name !== "AbortError") {
        console.warn("Career progress stream closed", error);
        void refreshTaskSilently(id);
      }
    });

    return () => {
      active = false;
      stream.cancel();
    };
  }, [refreshTaskSilently, task?.id, task?.status]);

  const quality = task?.qualityScore == null ? "-" : String(task.qualityScore);

  return (
    <MainLayout>
      <div className="h-full overflow-y-auto bg-[#F8FAFC]">
        <div className="mx-auto flex max-w-6xl flex-col gap-5 px-6 py-6">
          <div className="flex flex-col gap-3 rounded-lg border bg-white p-5 shadow-sm lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <WandSparkles className="h-5 w-5 text-primary" />
                <h1 className="text-2xl font-semibold text-slate-950">Resume Optimization</h1>
              </div>
              <p className="mt-2 text-sm text-slate-600">
                Inspect judge-executor output, score gates, risk review, progress events, and accepted suggestions.
              </p>
            </div>
            <Button asChild variant="outline">
              <Link to="/career">Back To Career</Link>
            </Button>
          </div>

          <div className="grid gap-4 lg:grid-cols-[380px_1fr]">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Create Or Load</CardTitle>
                <CardDescription>Load by task id, or create a new optimization task.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>taskId</Label>
                  <div className="flex gap-2">
                    <Input value={taskId} onChange={(event) => setTaskId(event.target.value)} placeholder="taskId" />
                    <Button variant="outline" onClick={() => loadTask()} disabled={loading}>
                      <RefreshCcw className="h-4 w-4" />
                      {loading ? "Loading" : "Load"}
                    </Button>
                  </div>
                </div>
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
                  <Input
                    value={createForm.alignmentReportId}
                    onChange={(event) =>
                      setCreateForm((prev) => ({ ...prev, alignmentReportId: event.target.value }))
                    }
                    placeholder="alignmentReportId"
                  />
                </div>
                <Button onClick={handleCreate} disabled={creating}>
                  {creating ? "Creating..." : "Create Optimization"}
                </Button>
              </CardContent>
            </Card>

            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-4">
                <Metric label="qualityScore" value={quality} />
                <Metric label="reviewStatus" value={task?.reviewStatus || "-"} />
                <Metric label="taskStatus" value={task?.status || "-"} />
                <Metric label="traceId" value={task?.traceId || "-"} />
              </div>

              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Risk And Delivery Gate</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="rounded-lg border bg-amber-50 p-3 text-sm text-amber-900">
                    {task?.riskSummary || "No risk summary yet."}
                  </div>
                  <p className="text-sm text-slate-600">{task?.summary || "No optimization summary yet."}</p>
                  <Button onClick={handleGenerateVersion} disabled={generating || !task}>
                    <RotateCw className="h-4 w-4" />
                    {generating ? "Generating..." : "Generate Version"}
                  </Button>
                  {version ? (
                    <div className="rounded-lg border bg-slate-50 p-3 text-sm">
                      <p className="font-medium text-slate-900">Version: {version.id}</p>
                      <p className="mt-1 text-slate-600">
                        {version.title || "Untitled version"} / v{version.versionNo ?? "-"}
                      </p>
                    </div>
                  ) : null}
                </CardContent>
              </Card>
            </div>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-lg">progressEvents</CardTitle>
              <CardDescription>Visible checkpoints for generation, review, pass, or needs-review states.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {(task?.progressEvents || []).length === 0 ? (
                  <EmptyState text="No progress events yet." />
                ) : (
                  task?.progressEvents?.map((event) => (
                    <div key={event.id} className="rounded-lg border bg-white p-3 text-sm">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <Badge variant="outline">{event.eventType || "EVENT"}</Badge>
                        <span className="text-xs text-slate-500">{event.createTime || "-"}</span>
                      </div>
                      <p className="mt-2 text-slate-700">{event.message || "-"}</p>
                      {event.payloadJson ? (
                        <pre className="mt-2 overflow-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100">
                          {event.payloadJson}
                        </pre>
                      ) : null}
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-lg">suggestions</CardTitle>
              <CardDescription>Accept, reject, or edit suggestions before generating an optimized version.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid gap-3">
                {(task?.suggestions || []).length === 0 ? (
                  <EmptyState text="No suggestions yet." />
                ) : (
                  task?.suggestions?.map((suggestion) => (
                    <div key={suggestion.id} className="rounded-lg border bg-white p-4">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <div className="flex flex-wrap items-center gap-2">
                            <h3 className="font-semibold text-slate-950">{suggestion.title || "Untitled suggestion"}</h3>
                            <Badge variant="secondary">{suggestion.category || "general"}</Badge>
                            <Badge variant="outline">{suggestion.riskLevel || "risk -"}</Badge>
                            <Badge>{suggestion.status || "PENDING"}</Badge>
                          </div>
                          <p className="mt-2 text-sm text-slate-600">{suggestion.reason || "No reason."}</p>
                        </div>
                        <div className="flex gap-2">
                          <Button size="sm" variant="outline" onClick={() => handleDecision(suggestion, "ACCEPTED")}>
                            <Check className="h-4 w-4" />
                            Accept
                          </Button>
                          <Button size="sm" variant="outline" onClick={() => handleDecision(suggestion, "REJECTED")}>
                            <X className="h-4 w-4" />
                            Reject
                          </Button>
                        </div>
                      </div>
                      <div className="mt-3 grid gap-3 md:grid-cols-2">
                        <TextBox label="originalText" value={suggestion.originalText} />
                        <TextBox label="suggestedText" value={suggestion.suggestedText} />
                      </div>
                      <Textarea
                        className="mt-3"
                        value={editedText[suggestion.id] ?? suggestion.suggestedText ?? ""}
                        onChange={(event) =>
                          setEditedText((prev) => ({ ...prev, [suggestion.id]: event.target.value }))
                        }
                        rows={3}
                        placeholder="Edit suggestion text before accepting"
                      />
                      <Button className="mt-2" size="sm" onClick={() => handleDecision(suggestion, "EDITED")}>
                        Accept Edited Text
                      </Button>
                    </div>
                  ))
                )}
              </div>
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

function TextBox({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="rounded-lg bg-slate-50 p-3 text-sm">
      <p className="mb-2 text-xs font-medium text-slate-500">{label}</p>
      <p className="whitespace-pre-wrap text-slate-700">{value || "-"}</p>
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return <div className="rounded-lg border border-dashed bg-slate-50 p-6 text-center text-sm text-slate-500">{text}</div>;
}

function mergeTaskProgress(
  previous: CareerOptimizationTask | null,
  next: CareerOptimizationTask
): CareerOptimizationTask {
  if (!previous || previous.id !== next.id) {
    return next;
  }
  return {
    ...next,
    progressEvents: mergeProgressEvents(previous.progressEvents, next.progressEvents)
  };
}

function mergeProgressEvents(
  current: CareerProgressEvent[] = [],
  incoming: CareerProgressEvent[] = []
): CareerProgressEvent[] {
  const eventsByKey = new Map<string, CareerProgressEvent>();
  [...current, ...incoming].forEach((event) => {
    eventsByKey.set(progressEventKey(event), event);
  });
  return Array.from(eventsByKey.values()).sort(compareProgressEvents);
}

function progressEventKey(event: CareerProgressEvent) {
  return event.id || [event.eventType, event.createTime, event.message, event.payloadJson].join("|");
}

function compareProgressEvents(left: CareerProgressEvent, right: CareerProgressEvent) {
  const leftTime = left.createTime ? Date.parse(left.createTime) : 0;
  const rightTime = right.createTime ? Date.parse(right.createTime) : 0;
  if (leftTime !== rightTime) {
    return leftTime - rightTime;
  }
  return progressEventKey(left).localeCompare(progressEventKey(right));
}
