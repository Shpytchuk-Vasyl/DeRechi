# Developer guide

How a change travels from a ticket to production in this repository. The pages are in the
order you will need them; the one to read end to end is
[development workflow](development-workflow.md).

- [Getting started](getting-started.md): tools, first run, first notice, first match.
- [Development workflow](development-workflow.md): the full loop from ticket to closed ticket.
- [Branches and commits](branches-and-commits.md): naming, commit messages, what not to commit.
- [Pull requests](pull-requests.md): the PR template, size, draft PRs, merging.
- [Testing locally](testing-locally.md): running the test suites and checking things by hand.
- [Stack tests](stack-tests.md): integration and load tests on an isolated stack, one command, the report.
- [Troubleshooting](troubleshooting.md): the errors everybody hits once, with fixes.

## The loop in ten lines

1. Read the ticket and reproduce the problem if it is a bug.
2. Find the feature in [features/](../features/README.md) and check
   [extending/](../extending/README.md) for a ready-made checklist.
3. Branch from a fresh `main` (`feature/...`, `fix/...`).
4. Implement, following the [conventions](../conventions/README.md); keep entity copies and
   message bundles in sync.
5. Write tests; run them with Docker up.
6. If the schema changed, generate, review and apply a migration.
7. Run the stack locally and check the change by hand.
8. Update the docs that describe what you changed.
9. Push, open a PR with the template, get a review, merge, delete the branch.
10. Verify on staging (the full compose stack until a staging host exists), then close the
    ticket with what changed and how you checked it.
