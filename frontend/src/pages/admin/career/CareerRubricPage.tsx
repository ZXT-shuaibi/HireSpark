import * as React from "react";
import { RefreshCcw } from "lucide-react";
import { toast } from "sonner";

import { listCareerAdminRubrics, type CareerAdminRubric } from "@/services/careerService";
import { getErrorMessage } from "@/utils/error";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export function CareerRubricPage() {
  const [rubrics, setRubrics] = React.useState<CareerAdminRubric[]>([]);
  const [loading, setLoading] = React.useState(false);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const result = await listCareerAdminRubrics();
      setRubrics(result || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load rubric templates."));
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
          <h1 className="text-2xl font-semibold text-slate-950">Career Rubrics</h1>
          <p className="mt-1 text-sm text-slate-600">Read-only rubric templates for MVP review and scoring.</p>
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCcw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          Refresh
        </Button>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        {rubrics.length === 0 ? (
          <Card>
            <CardContent className="p-6 text-sm text-slate-500">No rubric templates returned yet.</CardContent>
          </Card>
        ) : (
          rubrics.map((rubric) => (
            <Card key={rubric.id}>
              <CardHeader>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <CardTitle className="text-lg">{rubric.name || rubric.id}</CardTitle>
                    <CardDescription>{rubric.version || "-"}</CardDescription>
                  </div>
                  <Badge variant={rubric.editable ? "default" : "secondary"}>{rubric.editable ? "Editable" : "Read Only"}</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {(rubric.dimensions || []).length === 0 ? (
                  <div className="rounded-lg border border-dashed bg-slate-50 p-4 text-sm text-slate-500">
                    No rubric dimensions available.
                  </div>
                ) : (
                  rubric.dimensions?.map((dimension, index) => (
                    <div key={dimension.id || `${rubric.id}-${index}`} className="rounded-lg border bg-white p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="font-medium text-slate-950">{dimension.name || "Unnamed dimension"}</p>
                        <span className="text-xs text-slate-500">Weight {dimension.weight ?? "-"}</span>
                      </div>
                      <p className="mt-2 text-sm text-slate-600">{dimension.description || "-"}</p>
                      {(dimension.signals || []).length > 0 ? (
                        <div className="mt-3 flex flex-wrap gap-2">
                          {dimension.signals?.map((signal) => (
                            <Badge key={signal} variant="outline">
                              {signal}
                            </Badge>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
