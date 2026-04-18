export function formatFileSize(size?: number | null): string {
  if (size === null || size === undefined) {
    return '--';
  }
  if (size === 0) {
    return '0 B';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export function statusClass(status?: string | null): string {
  switch (status) {
    case 'COMPLETED':
    case 'EVALUATED':
      return 'status-pill bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300';
    case 'PROCESSING':
    case 'IN_PROGRESS':
      return 'status-pill bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300';
    case 'FAILED':
    case 'CANCELLED':
      return 'status-pill bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-300';
    default:
      return 'status-pill bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300';
  }
}
