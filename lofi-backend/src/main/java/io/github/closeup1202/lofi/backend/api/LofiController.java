package io.github.closeup1202.lofi.backend.api;

import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.backend.service.LofiCommandService;
import io.github.closeup1202.lofi.backend.service.LofiQueryService;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.view.DeploySnapshotView;
import io.github.closeup1202.lofi.core.view.DiffResultView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    public LofiController(LofiQueryService queryService, LofiCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
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

    @GetMapping("/{commitHash}")
    public DeploySnapshotView snapshot(@PathVariable @NotBlank String commitHash) {
        return queryService.snapshot(commitHash);
    }

    @GetMapping("/diff")
    public DiffResultView diff(@RequestParam @NotBlank String base, @RequestParam @NotBlank String head) {
        return DiffResultView.from(queryService.diff(base, head));
    }
}
