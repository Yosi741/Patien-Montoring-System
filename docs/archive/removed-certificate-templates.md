# Removed Certificate Templates

Earlier versions experimented with blank PDF certificate templates under `data/certificate_templates/` and duplicate certificate output folders under `data/certificates/`.

For the presentation version, certificate output is standardized under:

- `data/generated/birth-certificates/`
- `data/generated/death-certificates/`

The active demo does not require PDF template overlay files. Generated certificate metadata is stored in SQLite, and generated local certificate files are stored under `data/generated/`.
