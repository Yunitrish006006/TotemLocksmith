# Local manual visual review

Run the client GUI GameTests first so the screenshots are copied into
`test-artifacts/screenshots/locksmith-management/`, then run:

```bash
bash scripts/manual-visual-review.sh
```

Open the listed PNG files locally and review them at native scale and integer
zoom. The GitHub workflow keeps objective 16x16 format validation and automated
test execution; subjective visual approval is local and must be recorded before
publishing.
