import * as React from "react";
import { Activity, AlertTriangle, FileText, GitBranch, Layers3, RefreshCcw, ShieldAlert } from "lucide-react";
import { toast } from "sonner";

import { getCareerAdminOverview, listCareerAdminTasks, type CareerAdminOverview, type CareerAdminTask } from "@/services/careerService";
import { getErrorMessage } from "@/utils/error";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Link } from "react-router-dom";

const statCards = [
  { key: "resumeDocuments", label: "Resume Documents", icon: FileText },
  { key: "jobDescriptions", label: "JD Records", icon: Layers3 },
  { key: "optimizationTasks", label: "Optimization Tasks", icon: GitBranch },
  { key: "interviewSessions", label: "Interview Sessions", icon: Activity },
  { key: "singleFlightRecords", label: "Single-flight Records", icon: ShieldAlert },
  { key: "taskAttempts", label: "AI Attempts", icon: RefreshCcw },
  { key: "failedExports", label: "Failed Exports", icon: AlertTriangle }
] as const;

export function CareerDashboardPage() {
  const [overview, setOverview] = React.useState<CareerAdminOverview | null>(null);
  const [tasks, setTasks] = React.useState<CareerAdminTask[]>([]);
  const [loading, setLoading] = React.useState(false);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const [overviewData, taskData] = await Promise.all([
        getCareerAdminOverview(),
        listCareerAdminTasks({ limit: 10 })
      ]);
      setOverview(overviewData);
      setTasks(taskData || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load career admin dashboard."));
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="admin-page space-y-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-950">Career Dashboard</h1>
          <p className="mt-1 text-sm text-slate-600">
            Review optimization quality, interview runtime state, single-flight governance, and export failures.
          </p>
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCcw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          Refresh
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {statCards.map((item) => {
          const Icon = item.icon;
          const value = overview ? (overview as Record<string, number | null | undefined>)[item.key] : null;
          return (
            <Card key={item.key}>
              <CardContent className="p-5">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-sm text-slate-500">{item.label}</p>
                    <p className="mt-2 text-3xl font-semibold text-slate-950">{value ?? "-"}</p>
                  </div>
                  <div className="rounded-xl bg-slate-100 p-3 text-slate-700">
                    <Icon className="h-5 w-5" />
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title="Optimization Quality" value={overview?.optimizationReviewPassed ?? "-"} detail="Passed" />
        <MetricCard title="Needs Revision" value={overview?.optimizationReviewNeedsRevision ?? "-"} detail="Gate below 0.8" />
        <MetricCard title="Blocked by Risk" value={overview?.optimizationReviewBlockedByRisk ?? "-"} detail="Truthfulness risk" />
        <MetricCard title="Single-flight" value={overview?.singleFlightRunning ?? "-"} detail="Running" />
        <MetricCard title="Attempt Replay" value={overview?.taskAttemptReplayed ?? "-"} detail="Reused AI result" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Recent Career Tasks</CardTitle>
          <CardDescription>Resume, JD, optimization, interview, export, and single-flight records.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>User</TableHead>
                <TableHead>Business</TableHead>
                <TableHead>Summary</TableHead>
                <TableHead>Trace</TableHead>
                <TableHead>AI</TableHead>
                <TableHead>Time</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tasks.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="py-8 text-center text-slate-500">
                    No task records yet.
                  </TableCell>
                </TableRow>
              ) : (
                tasks.map((task) => (
                  <TableRow key={task.id}>
                    <TableCell className="font-medium">{task.type || "-"}</TableCell>
                    <TableCell>
                      <Badge variant={task.status === "FAILED" ? "destructive" : task.status === "SUCCESS" ? "secondary" : "outline"}>
                        {task.status || "-"}
                      </Badge>
                    </TableCell>
                    <TableCell>{task.userId || "-"}</TableCell>
                    <TableCell>{task.businessId || "-"}</TableCell>
                    <TableCell className="max-w-[420px] break-words text-slate-600">
                      {task.summary || task.failureReason || "-"}
                    </TableCell>
                    <TableCell className="text-sm text-slate-500">{task.traceId || "-"}</TableCell>
                    <TableCell className="text-sm text-slate-500">
                      {task.modelName ? `${task.modelName}${task.replayed ? " / replay" : ""}` : "-"}
                    </TableCell>
                    <TableCell className="text-sm text-slate-500">{task.createTime || "-"}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button asChild variant="outline">
          <Link to="/admin/career/tasks">Open Task Center</Link>
        </Button>
      </div>
    </div>
  );
}

function MetricCard({ title, value, detail }: { title: string; value: React.ReactNode; detail: string }) {
  return (
    <Card>
      <CardContent className="p-5">
        <p className="text-sm text-slate-500">{title}</p>
        <p className="mt-2 text-3xl font-semibold text-slate-950">{value}</p>
        <p className="mt-2 text-xs text-slate-400">{detail}</p>
      </CardContent>
    </Card>
  );
}
