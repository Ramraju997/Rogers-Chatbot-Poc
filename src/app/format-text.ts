/**
 * Turns the light Markdown the agent writes (**bold**, "* " / "- " bullets, line breaks)
 * into HTML. Everything is HTML-escaped first, so agent text can't inject markup.
 */
export function formatBotText(text: string): string {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

  const inline = (line: string) => line.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

  const html: string[] = [];
  let inList = false;
  for (const raw of escaped.split('\n')) {
    const bullet = raw.match(/^\s*[*-]\s+(.*)$/);
    if (bullet) {
      if (!inList) html.push('<ul>');
      inList = true;
      html.push(`<li>${inline(bullet[1])}</li>`);
      continue;
    }
    if (inList) {
      html.push('</ul>');
      inList = false;
    }
    html.push(raw.trim() ? `<p>${inline(raw)}</p>` : '');
  }
  if (inList) html.push('</ul>');
  return html.join('');
}
