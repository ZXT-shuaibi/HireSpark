/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.career.controller;

import com.nageoffer.ai.ragent.career.controller.request.CareerResumeExportRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerResumeUpdateRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeExportVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeUploadVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeVersionVO;
import com.nageoffer.ai.ragent.career.service.CandidateProfileService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CareerResumeController {

    private final CandidateProfileService candidateProfileService;

    @PostMapping(value = "/career/resumes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<CareerResumeUploadVO> upload(@RequestPart("file") MultipartFile file) {
        return Results.success(candidateProfileService.uploadAndParse(file));
    }

    @GetMapping("/career/resumes/versions/{versionId}")
    public Result<CareerResumeVersionVO> queryVersion(@PathVariable String versionId) {
        return Results.success(candidateProfileService.queryVersion(versionId));
    }

    @GetMapping("/career/profiles/{profileId}/versions")
    public Result<List<CareerResumeVersionVO>> listVersions(@PathVariable String profileId) {
        return Results.success(candidateProfileService.listVersions(profileId));
    }

    @PutMapping("/career/resumes/versions/{versionId}")
    public Result<CareerResumeVersionVO> updateVersion(@PathVariable String versionId,
                                                       @RequestBody CareerResumeUpdateRequest request) {
        return Results.success(candidateProfileService.updateVersion(versionId, request));
    }

    @DeleteMapping("/career/resumes/versions/{versionId}")
    public Result<Void> deleteVersion(@PathVariable String versionId) {
        candidateProfileService.deleteVersion(versionId);
        return Results.success();
    }

    @PostMapping("/career/resumes/export")
    public Result<CareerResumeExportVO> export(@RequestBody CareerResumeExportRequest request) {
        return Results.success(candidateProfileService.export(request));
    }
}
