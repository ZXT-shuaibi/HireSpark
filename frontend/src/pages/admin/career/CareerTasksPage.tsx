import * as React from "react";
import { RefreshCcw, Search } from "lucide-react";
import { toast } from "sonner";

import { getCareerAdminOverview, listCareerAdminTasks, type CareerAdminOverview, type CareerAdminTask } from "@/services/careerService";
import { getErrorMessage } from "@/utils/error";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Link } from "react-router-dom";

const ALL_VALUE = "__all__";

const taskTypes = [
  { value: ALL_VALUE, label: "All Types" },
  { value: "RESUME_DOCUMENT", label: "Resume Document" },
  { value: "ALIGNMENT_REPORT", label: "Alignment Report" },
  { value: "OPTIMIZATION_TASK", label: "Optimization Task" },
  { value: "INTERVIEW_SESSION", label: "Interview Session" },
  { value: "INTERVIEW_REPORT", label: "Interview Report" },
  { value: "EXPORT_RECORD", label: "Export Record" },
  { value: "SINGLE_FLIGHT", label: "Single-flight" },
  { value: "TASK_ATTEMPT", label: "AI Attempt" }
];

const taskStatuses = [
  { value: ALL_VALUE, label: "All Statuses" },
  { value: "PENDING", label: "PENDING" },
  { value: "RUNNING", label: "RUNNING" },
  { value: "SUCCESS", label: "SUCCESS" },
  { value: "REPLAYED", label: "REPLAYED" },
  { value: "NEEDS_REVIEW", label: "NEEDS_REVIEW" },
  { value: "FAILED", label: "FAILED" },
  { value: "CANCELLED", label: "CANCELLED" }
];

export function CareerTasksPage() {
  const [overview, setOverview] = React.useState<CareerAdminOverview | null>(null);
  const [tasks, setTasks] = React.useState<CareerAdminTask[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [query, setQuery] = React.useState({
    limit: "20",
    type: "",
    status: ""
  });

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const [overviewData, taskData] = await Promise.all([
        getCareerAdminOverview(),
        listCareerAdminTasks({
          limit: Number(query.limit) || 20,
          type: query.type || undefined,
          status: query.status || undefined
        })
      ]);
      setOverview(overviewData);
      setTasks(taskData || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load career task center."));
    } finally {
      setLoading(false);
    }
  }, [query.limit, query.status, query.type]);

  React.useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="admin-page space-y-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-950">Career Task Center</h1>
          <p className="mt-1 text-sm text-slate-600">
            Filter optimization, interview, export, and single-flight records.
          </p>
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCcw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          Refresh
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <Stat label="Optimization Tasks" value={overview?.optimizationTasks ?? "-"} />
        <Stat label="Review Passed" value={overview?.optimizationReviewPassed ?? "-"} />
        <Stat label="Interview Sessions" value={overview?.interviewSessions ?? "-"} />
        <Stat label="AI Attempts" value={overview?.taskAttempts ?? "-"} />
        <Stat label="Failed Attempts" value={overview?.taskAttemptFailed ?? "-"} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Filters</CardTitle>
          <CardDescription>Use type and status to narrow the task stream.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 md:grid-cols-[160px_160px_1fr_120px]">
            <Select
              value={query.type || ALL_VALUE}
              onValueChange={(value) => setQuery((prev) => ({ ...prev, type: value === ALL_VALUE ? "" : value }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Type" />
              </SelectTrigger>
              <SelectContent>
                {taskTypes.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={query.status || ALL_VALUE}
              onValueChange={(value) => setQuery((prev) => ({ ...prev, status: value === ALL_VALUE ? "" : value }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                {taskStatuses.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Input
              value={query.limit}
              onChange={(event) => setQuery((prev) => ({ ...prev, limit: event.target.value }))}
              inputMode="numeric"
              placeholder="Limit"
            />

            <Button variant="outline" onClick={() => void load()} disabled={loading}>
              <Search className="h-4 w-4" />
              Apply
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Task Stream</CardTitle>
          <CardDescription>Look at qualityScore, reviewStatus, runtimeStatus, and single-flight state together.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Quality</TableHead>
                <TableHead>Runtime</TableHead>
                <TableHead>Risk</TableHead>
                <TableHead>Scene</TableHead>
                <TableHead>AI</TableHead>
                <TableHead>Trace</TableHead>
                <TableHead>Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tasks.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} className="py-8 text-center text-slate-500">
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
                    <TableCell>{task.qualityScore ?? "-"}</TableCell>
                    <TableCell>{task.runtimeStatus || "-"}</TableCell>
                    <TableCell>{task.riskFlag ? "true" : "false"}</TableCell>
                    <TableCell>{task.scene || "-"}</TableCell>
                    <TableCell>
                      {task.modelName ? `${task.modelName}${task.replayed ? " / replay" : ""}` : "-"}
                      {task.latencyMs != null ? ` / ${task.latencyMs}ms` : ""}
                    </TableCell>
                    <TableCell className="text-sm text-slate-500">{task.traceId || "-"}</TableCell>
                    <TableCell>
                      <Button asChild size="sm" variant="outline">
                        <Link to="/admin/career">Open Dashboard</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Card>
      <CardContent className="p-5">
        <p className="text-sm text-slate-500">{label}</p>
        <p className="mt-2 text-3xl font-semibold text-slate-950">{value}</p>
      </CardContent>
    </Card>
  );
}
