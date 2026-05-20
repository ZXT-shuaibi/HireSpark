import * as React from "react";
import { Activity, RefreshCcw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { listCareerAdminAgentTraces, type CareerAdminAgentTrace } from "@/services/careerService";
import { getErrorMessage } from "@/utils/error";

const ALL_VALUE = "__all__";

const traceStatuses = [
  { value: ALL_VALUE, label: "All Statuses" },
  { value: "RUNNING", label: "RUNNING" },
  { value: "SUCCESS", label: "SUCCESS" },
  { value: "FAILED", label: "FAILED" }
];

const agentTypes = [
  { value: ALL_VALUE, label: "All Agents" },
  { value: "LLM", label: "LLM" },
  { value: "INTERVIEW_PLAN", label: "Interview Plan" },
  { value: "INTERVIEW_EVALUATE", label: "Interview Evaluate" },
  { value: "RESUME_OPTIMIZE", label: "Resume Optimize" },
  { value: "RESUME_REVIEW", label: "Resume Review" }
];

export function CareerAgentTracesPage() {
  const [traces, setTraces] = React.useState<CareerAdminAgentTrace[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [query, setQuery] = React.useState({
    limit: "20",
    agentType: "",
    status: ""
  });

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const data = await listCareerAdminAgentTraces({
        limit: Number(query.limit) || 20,
        agentType: query.agentType || undefined,
        status: query.status || undefined
      });
      setTraces(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load Career Agent traces."));
    } finally {
      setLoading(false);
    }
  }, [query.agentType, query.limit, query.status]);

  React.useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="admin-page space-y-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <Activity className="h-5 w-5 text-primary" />
            <h1 className="text-2xl font-semibold text-slate-950">Career Agent Traces</h1>
          </div>
          <p className="mt-1 text-sm text-slate-600">
            Inspect agent calls, latency, model usage, replay output, and failure reasons.
          </p>
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCcw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          Refresh
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Filters</CardTitle>
          <CardDescription>Filter recent Agent executions by agent type and status.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 md:grid-cols-[180px_160px_1fr_120px]">
            <Select
              value={query.agentType || ALL_VALUE}
              onValueChange={(value) => setQuery((prev) => ({ ...prev, agentType: value === ALL_VALUE ? "" : value }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Agent" />
              </SelectTrigger>
              <SelectContent>
                {agentTypes.map((item) => (
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
                {traceStatuses.map((item) => (
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
          <CardTitle className="text-lg">Recent Executions</CardTitle>
          <CardDescription>Agent-level observability for the Career workflow.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Agent</TableHead>
                <TableHead>Scene</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Model</TableHead>
                <TableHead>Latency</TableHead>
                <TableHead>Trace</TableHead>
                <TableHead>Summary</TableHead>
                <TableHead>Updated</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {traces.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="py-8 text-center text-slate-500">
                    No Agent trace records yet.
                  </TableCell>
                </TableRow>
              ) : (
                traces.map((trace) => (
                  <TableRow key={trace.id}>
                    <TableCell className="font-medium">{trace.agentType || "-"}</TableCell>
                    <TableCell>{trace.scene || "-"}</TableCell>
                    <TableCell>
                      <Badge variant={trace.status === "FAILED" ? "destructive" : trace.status === "SUCCESS" ? "secondary" : "outline"}>
                        {trace.status || "-"}
                      </Badge>
                    </TableCell>
                    <TableCell>{trace.modelName || "-"}</TableCell>
                    <TableCell>{trace.latencyMs == null ? "-" : `${trace.latencyMs}ms`}</TableCell>
                    <TableCell className="max-w-[160px] truncate text-sm text-slate-500">{trace.traceId || "-"}</TableCell>
                    <TableCell className="max-w-[360px]">
                      <div className="space-y-1 text-sm">
                        <p className="line-clamp-2 text-slate-700">{trace.outputSummary || trace.inputSummary || "-"}</p>
                        {trace.errorMessage ? <p className="line-clamp-2 text-rose-600">{trace.errorMessage}</p> : null}
                      </div>
                    </TableCell>
                    <TableCell className="text-sm text-slate-500">{trace.updateTime || trace.createTime || "-"}</TableCell>
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
