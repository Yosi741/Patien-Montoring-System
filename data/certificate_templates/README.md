Place optional blank certificate templates here:

- birth_template.pdf
- death_template.pdf

If either template exists, certificate generation uses Apache PDFBox to overlay text and an optional signature image on top of the blank PDF.

Expected file names:

- `birth_template.pdf`
- `death_template.pdf`

Template notes:

- Use a one-page blank PDF template when possible.
- Leave open space around the upper-middle and lower-right areas for overlaid certificate data and signature image.
- The app writes generated PDFs to `data/certificates/birth/` and `data/certificates/death/`.
- If a template is missing, the app automatically uses the internal fallback PDF generator.
