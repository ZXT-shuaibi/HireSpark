import * as React from "react";
import { Link, useNavigate } from "react-router-dom";
import { BriefcaseBusiness, FileUp, Gauge, MessageSquareText, Target } from "lucide-react";
import { toast } from "sonner";

import { MainLayout } from "@/components/layout/MainLayout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  type CareerAlignmentReport,
  type CareerJob,
  type CareerResumeExport,
  type CareerResumeUpload,
  createCareerAlignment,
  createCareerJob,
  exportCareerResume,
  uploadCareerResume
} from "@/services/careerService";
import { getErrorMessage } from "@/utils/error";

const flowCards = [
  { title: "Resume", description: "Parse a versioned career profile.", icon: FileUp },
  { title: "JD Align", description: "Score evidence, gaps, and risks.", icon: Target },
  { title: "Optimize", description: "Gate suggestions through review.", icon: Gauge },
  { title: "Interview", description: "Practice with recoverable turns.", icon: MessageSquareText }
];

export function CareerHomePage() {
  const navigate = useNavigate();
  const [resumeFile, setResumeFile] = React.useState<File | null>(null);
  const [uploading, setUploading] = React.useState(false);
  const [uploadResult, setUploadResult] = React.useState<CareerResumeUpload | null>(null);
  const [exportVersionId, setExportVersionId] = React.useState("");
  const [exportType, setExportType] = React.useState("PDF");
  const [exporting, setExporting] = React.useState(false);
  const [exportResult, setExportResult] = React.useState<CareerResumeExport | null>(null);
  const [jobForm, setJobForm] = React.useState({ title: "", company: "", rawText: "" });
  const [creatingJob, setCreatingJob] = React.useState(false);
  const [jobResult, setJobResult] = React.useState<CareerJob | null>(null);
  const [alignForm, setAlignForm] = React.useState({ resumeVersionId: "", jdId: "" });
  const [aligning, setAligning] = React.useState(false);
  const [alignResult, setAlignResult] = React.useState<CareerAlignmentReport | null>(null);

  const handleUpload = async () => {
    if (!resumeFile) {
      toast.error("Choose a resume file first.");
      return;
    }
    setUploading(true);
    try {
      const result = await uploadCareerResume(resumeFile);
      setUploadResult(result);
      setExportVersionId(result.resumeVersionId || "");
      setAlignForm((prev) => ({ ...prev, resumeVersionId: result.resumeVersionId || prev.resumeVersionId }));
      toast.success("Resume uploaded.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Resume upload failed."));
    } finally {
      setUploading(false);
    }
  };

  const handleExport = async () => {
    const resumeVersionId = exportVersionId.trim();
    if (!resumeVersionId) {
      toast.error("Enter resumeVersionId.");
      return;
    }
    setExporting(true);
    try {
      const result = await exportCareerResume(resumeVersionId, exportType.trim() || "PDF");
      setExportResult(result);
      toast.success("Export task created.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Resume export failed."));
    } finally {
      setExporting(false);
    }
  };

  const handleCreateJob = async () => {
    if (!jobForm.rawText.trim()) {
      toast.error("Paste JD text first.");
      return;
    }
    setCreatingJob(true);
    try {
      const result = await createCareerJob({
        title: jobForm.title.trim(),
        company: jobForm.company.trim(),
        rawText: jobForm.rawText.trim(),
        sourceType: "manual"
      });
      setJobResult(result);
      setAlignForm((prev) => ({ ...prev, jdId: result.id || prev.jdId }));
      toast.success("JD created.");
    } catch (error) {
      toast.error(getErrorMessage(error, "JD creation failed."));
    } finally {
      setCreatingJob(false);
    }
  };

  const handleAlign = async () => {
    const resumeVersionId = alignForm.resumeVersionId.trim();
    const jdId = alignForm.jdId.trim();
    if (!resumeVersionId || !jdId) {
      toast.error("Enter resumeVersionId and jdId.");
      return;
    }
    setAligning(true);
    try {
      const result = await createCareerAlignment(resumeVersionId, jdId);
      setAlignResult(result);
      toast.success("Alignment completed.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Alignment failed."));
    } finally {
      setAligning(false);
    }
  };

  return (
    <MainLayout>
      <div className="h-full overflow-y-auto bg-[#F8FAFC]">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-6 py-6">
          <div className="flex flex-col gap-4 rounded-lg border bg-white p-5 shadow-sm lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <BriefcaseBusiness className="h-5 w-5 text-primary" />
                <h1 className="text-2xl font-semibold text-slate-950">Ragent Career</h1>
              </div>
              <p className="mt-2 max-w-2xl text-sm text-slate-600">
                A working career loop for resume parsing, JD alignment, judge-executor optimization, and
                recoverable mock interviews.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="outline">
                <Link to="/career/optimizations">Optimization</Link>
              </Button>
              <Button asChild>
                <Link to="/career/interviews">Interview</Link>
              </Button>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-4">
            {flowCards.map(({ title, description, icon: Icon }) => (
              <Card key={title}>
                <CardContent className="flex items-start gap-3 p-4">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="font-semibold text-slate-950">{title}</p>
                    <p className="mt-1 text-xs text-slate-500">{description}</p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Resume Upload And Export</CardTitle>
                <CardDescription>Upload a resume, capture its version id, and exercise the render gate.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>Resume File</Label>
                  <Input type="file" onChange={(event) => setResumeFile(event.target.files?.[0] || null)} />
                </div>
                <Button onClick={handleUpload} disabled={uploading}>
                  {uploading ? "Uploading..." : "Upload And Parse"}
                </Button>
                {uploadResult ? (
                  <ResultBlock
                    title="Upload Result"
                    rows={[
                      ["documentId", uploadResult.documentId],
                      ["profileId", uploadResult.profileId],
                      ["resumeVersionId", uploadResult.resumeVersionId],
                      ["parseStatus", uploadResult.parseStatus]
                    ]}
                  />
                ) : null}
                <div className="grid gap-3 sm:grid-cols-[1fr_120px_auto]">
                  <Input
                    value={exportVersionId}
                    onChange={(event) => setExportVersionId(event.target.value)}
                    placeholder="resumeVersionId"
                  />
                  <Input value={exportType} onChange={(event) => setExportType(event.target.value)} placeholder="PDF" />
                  <Button variant="outline" onClick={handleExport} disabled={exporting}>
                    {exporting ? "Exporting..." : "Export"}
                  </Button>
                </div>
                {exportResult ? (
                  <ResultBlock
                    title="Export Result"
                    rows={[
                      ["id", exportResult.id],
                      ["status", exportResult.status],
                      ["error", exportResult.errorMessage],
                      ["traceId", exportResult.traceId]
                    ]}
                  />
                ) : null}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg">JD Alignment</CardTitle>
                <CardDescription>Create a JD, then align it with a resume version.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-3 sm:grid-cols-2">
                  <Input
                    value={jobForm.title}
                    onChange={(event) => setJobForm((prev) => ({ ...prev, title: event.target.value }))}
                    placeholder="Job title"
                  />
                  <Input
                    value={jobForm.company}
                    onChange={(event) => setJobForm((prev) => ({ ...prev, company: event.target.value }))}
                    placeholder="Company"
                  />
                </div>
                <Textarea
                  value={jobForm.rawText}
                  onChange={(event) => setJobForm((prev) => ({ ...prev, rawText: event.target.value }))}
                  placeholder="Paste JD text"
                  rows={5}
                />
                <Button onClick={handleCreateJob} disabled={creatingJob}>
                  {creatingJob ? "Creating..." : "Create JD"}
                </Button>
                {jobResult ? (
                  <ResultBlock
                    title="JD Result"
                    rows={[
                      ["jdId", jobResult.id],
                      ["title", jobResult.title],
                      ["company", jobResult.company]
                    ]}
                  />
                ) : null}
                <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto]">
                  <Input
                    value={alignForm.resumeVersionId}
                    onChange={(event) => setAlignForm((prev) => ({ ...prev, resumeVersionId: event.target.value }))}
                    placeholder="resumeVersionId"
                  />
                  <Input
                    value={alignForm.jdId}
                    onChange={(event) => setAlignForm((prev) => ({ ...prev, jdId: event.target.value }))}
                    placeholder="jdId"
                  />
                  <Button variant="outline" onClick={handleAlign} disabled={aligning}>
                    {aligning ? "Aligning..." : "Align"}
                  </Button>
                </div>
                {alignResult ? (
                  <div className="rounded-lg border bg-slate-50 p-3 text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-medium text-slate-900">Report: {alignResult.id}</span>
                      <Badge variant="secondary">Score {alignResult.score ?? "-"}</Badge>
                    </div>
                    <p className="mt-2 text-slate-600">{alignResult.summary || "No summary yet."}</p>
                    <Button
                      className="mt-3"
                      size="sm"
                      onClick={() => navigate(`/career/optimizations?alignmentReportId=${alignResult.id}`)}
                    >
                      Continue To Optimization
                    </Button>
                  </div>
                ) : null}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}

function ResultBlock({ title, rows }: { title: string; rows: Array<[string, React.ReactNode]> }) {
  return (
    <div className="rounded-lg border bg-slate-50 p-3 text-sm">
      <p className="mb-2 font-medium text-slate-900">{title}</p>
      <div className="space-y-1">
        {rows.map(([label, value]) => (
          <div key={label} className="grid grid-cols-[130px_1fr] gap-2">
            <span className="text-slate-500">{label}</span>
            <span className="break-all text-slate-800">{value || "-"}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
