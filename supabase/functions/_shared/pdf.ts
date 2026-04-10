export function createSimpleTextPdf(lines: string[]): Uint8Array {
  const contentLines = normalizePdfLines(lines);
  const encoder = new TextEncoder();
  const contentStream = buildContentStream(contentLines);
  const contentStreamBytes = encoder.encode(contentStream);

  const objects = [
    "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
    "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
    "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>\nendobj\n",
    `4 0 obj\n<< /Length ${contentStreamBytes.byteLength} >>\nstream\n${contentStream}\nendstream\nendobj\n`,
    "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
  ];

  const header = "%PDF-1.4\n";
  const headerBytes = encoder.encode(header);

  let offset = 0;
  const offsets = ["0000000000 65535 f \n"];
  const bodyParts: Uint8Array[] = [];

  offset += headerBytes.byteLength;
  bodyParts.push(headerBytes);

  for (const chunk of objects) {
    offsets.push(`${offset.toString().padStart(10, "0")} 00000 n \n`);
    const bytes = encoder.encode(chunk);
    bodyParts.push(bytes);
    offset += bytes.byteLength;
  }

  const xrefOffset = offset;
  const xref = `xref\n0 ${offsets.length}\n${offsets.join("")}trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`;

  const pdfBytes = new Uint8Array(offset + encoder.encode(xref).byteLength);
  let position = 0;
  for (const bytes of bodyParts) {
    pdfBytes.set(bytes, position);
    position += bytes.byteLength;
  }
  pdfBytes.set(encoder.encode(xref), position);

  return pdfBytes;
}

export function formatResumeJsonForPdf(resumeJson: Record<string, unknown>): string[] {
  const lines: string[] = [];
  const basics = resumeJson.basics as Record<string, unknown> | undefined;
  const education = Array.isArray(resumeJson.education) ? resumeJson.education : [];
  const skills = Array.isArray(resumeJson.skills) ? resumeJson.skills : [];
  const projects = Array.isArray(resumeJson.projects) ? resumeJson.projects : [];
  const experience = Array.isArray(resumeJson.experience) ? resumeJson.experience : [];
  const achievements = Array.isArray(resumeJson.achievements) ? resumeJson.achievements : [];

  lines.push("Internship Uncle Generated Resume");
  lines.push("");
  lines.push("Basics");
  if (basics) {
    pushKeyValue(lines, "Name", basics.name);
    pushKeyValue(lines, "Email", basics.email);
    pushKeyValue(lines, "Phone", basics.phone);
    pushKeyValue(lines, "Location", basics.location);
    pushKeyValue(lines, "LinkedIn", basics.linkedin);
    pushKeyValue(lines, "GitHub", basics.github);
    pushKeyValue(lines, "Portfolio", basics.portfolio);
  }

  pushListSection(lines, "Education", education, ["school", "degree", "start", "end", "gpa"]);
  pushFlatSection(lines, "Skills", skills);
  pushObjectListSection(lines, "Projects", projects, ["name", "description", "highlights"]);
  pushObjectListSection(lines, "Experience", experience, ["company", "role", "start", "end", "bullets"]);
  pushFlatSection(lines, "Achievements", achievements);

  return lines;
}

function buildContentStream(lines: string[]): string {
  const maxLines = 42;
  const displayedLines = lines.slice(0, maxLines);
  if (lines.length > maxLines) {
    displayedLines.push("... output truncated ...");
  }

  const commands: string[] = [
    "BT",
    "/F1 11 Tf",
    "1 0 0 1 50 740 Tm",
    "14 TL",
  ];

  for (const line of displayedLines) {
    commands.push(`(${escapePdfText(line)}) Tj`);
    commands.push("T*");
  }

  commands.push("ET");
  return commands.join("\n");
}

function normalizePdfLines(lines: string[]): string[] {
  return lines.map((line) => line.replace(/\r?\n/g, " ").trimEnd());
}

function pushKeyValue(lines: string[], key: string, value: unknown): void {
  if (typeof value === "string" && value.trim()) {
    lines.push(`${key}: ${value.trim()}`);
  }
}

function pushFlatSection(lines: string[], title: string, values: unknown[]): void {
  if (!values.length) {
    return;
  }

  lines.push("");
  lines.push(title);
  for (const value of values) {
    if (typeof value === "string" && value.trim()) {
      lines.push(`- ${value.trim()}`);
    }
  }
}

function pushListSection(lines: string[], title: string, values: unknown[], keys: string[]): void {
  if (!values.length) {
    return;
  }

  lines.push("");
  lines.push(title);
  for (const entry of values) {
    if (!entry || typeof entry !== "object") {
      continue;
    }

    const record = entry as Record<string, unknown>;
    const parts = keys
      .map((key) => record[key])
      .filter((value) => typeof value === "string" && value.trim())
      .map((value) => value.trim());

    if (parts.length > 0) {
      lines.push(`- ${parts.join(" | ")}`);
    }
  }
}

function pushObjectListSection(lines: string[], title: string, values: unknown[], keys: string[]): void {
  if (!values.length) {
    return;
  }

  lines.push("");
  lines.push(title);
  for (const entry of values) {
    if (!entry || typeof entry !== "object") {
      continue;
    }

    const record = entry as Record<string, unknown>;
    const heading = keys
      .slice(0, 2)
      .map((key) => record[key])
      .filter((value) => typeof value === "string" && value.trim())
      .map((value) => value.trim())
      .join(" - ");

    if (heading) {
      lines.push(`- ${heading}`);
    }

    for (const key of keys.slice(2)) {
      const value = record[key];
      if (Array.isArray(value)) {
        const joined = value
          .filter((item): item is string => typeof item === "string" && item.trim().length > 0)
          .map((item) => item.trim())
          .join(", ");
        if (joined) {
          lines.push(`  * ${key}: ${joined}`);
        }
      } else if (typeof value === "string" && value.trim()) {
        lines.push(`  * ${key}: ${value.trim()}`);
      }
    }
  }
}

function escapePdfText(value: string): string {
  return value
    .replace(/\\/g, "\\\\")
    .replace(/\(/g, "\\(")
    .replace(/\)/g, "\\)");
}
