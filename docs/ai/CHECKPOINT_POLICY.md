# Zavro Clean v2.0 — checkpointy i wznowienie
`docs/AI_HANDOFF.md` to jedyny bieżący checkpoint **konkretnej gałęzi**. Archiwum i `docs/AI_PROGRESS.md` są historią, nie źródłem aktualnego HEAD.
Zapisuj przy blokadach, ryzyku utraty postępu, zmianie kierunku i przed finalnym pushem wymagającym CI; nie po każdej operacji.
Checkpoint: task_id, workflow_mode=autonomous_task, repo, product_line, branch, base_sha, observed_head_sha, status, acceptance_source, required_ci, device, apk, changed_paths, verified_before_push, blockers, next_action i evidence_links.
Nie wpisuj przyszłego finalnego SHA/run ID jako fakt. Po CI raportuj run i link bez dodatkowego commita.
Po zmianie rozmowy odczytaj rzeczywisty HEAD i checkpoint z jawnego SHA. Stare dane nie nadpisują nowego kodu.
Brak możliwości oczekiwania na CI lub wykonania narzędzia: zostaw CI_PENDING lub BLOCKED, bez obietnicy automatycznego wznowienia.
