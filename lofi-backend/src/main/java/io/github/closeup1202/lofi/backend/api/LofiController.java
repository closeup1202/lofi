package io.github.closeup1202.lofi.backend.api;

import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.backend.api.response.VersionView;
import io.github.closeup1202.lofi.backend.service.LofiCommandService;
import io.github.closeup1202.lofi.backend.service.LofiQueryService;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.view.DeploySnapshotView;
import io.github.closeup1202.lofi.core.view.DiffResultView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/lofi")
public class LofiController {

    private final LofiQueryService queryService;
    private final LofiCommandService commandService;
    private final BuildProperties buildProperties;
    private final int retentionCommits;
    private final double regressionThreshold;

    public LofiController(LofiQueryService queryService,
                          LofiCommandService commandService,
                          BuildProperties buildProperties,
                          @Value("${lofi.backend.retention-commits}") int retentionCommits,
                          @Value("${lofi.backend.regression-threshold}") double regressionThreshold) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.buildProperties = buildProperties;
        this.retentionCommits = retentionCommits;
        this.regressionThreshold = regressionThreshold;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public void ingest(@RequestBody @Valid IngestRequest request) {
        commandService.ingest(request);
    }

    @GetMapping
    public List<CommitSummary> commits() {
        return queryService.listCommits();
    }

    @GetMapping("/version")
    public VersionView version() {
        return new VersionView(
                buildProperties.getName(),
                buildProperties.getVersion(),
                retentionCommits,
                regressionThreshold
        );
    }

    @GetMapping("/{commitHash}")
    public DeploySnapshotView snapshot(@PathVariable @NotBlank String commitHash) {
        return queryService.snapshot(commitHash);
    }

    @GetMapping("/diff")
    public DiffResultView diff(@RequestParam @NotBlank String base, @RequestParam @NotBlank String head) {
        return DiffResultView.from(queryService.diff(base, head));
    }
}
