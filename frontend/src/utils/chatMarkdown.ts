import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({
  gfm: true,
  breaks: true,
});

function normalizeMarkdownSpacing(content: string): string {
  return content
    .replace(/\r\n?/g, '\n')
    .replace(/\u00a0/g, ' ')
    .replace(/(^|\n)(#{1,6})([^\s#])/g, '$1$2 $3')
    .replace(/(^|\n)([-*])([^\s])/g, '$1$2 $3')
    .replace(/(^|\n)(\d+)\.([^\s])/g, '$1$2. $3')
    .replace(/(^|\n)>([^\s])/g, '$1> $2')
    .replace(/([^\n])\n(#{1,6}\s+)/g, '$1\n\n$2')
    .replace(/(#{1,6}\s+[^\n]+)\n([^\n#>-])/g, '$1\n\n$2')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

export function renderChatMarkdown(content: string): string {
  if (!content.trim()) {
    return '';
  }

  const normalized = normalizeMarkdownSpacing(content);
  const html = marked.parse(normalized, { async: false }) as string;
  return DOMPurify.sanitize(html);
}
