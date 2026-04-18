import dayjs from 'dayjs';

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '--';
  }
  return dayjs(value).format('YYYY-MM-DD HH:mm');
}

export function formatDateOnly(value?: string | null): string {
  if (!value) {
    return '--';
  }
  return dayjs(value).format('YYYY-MM-DD');
}

export function formatRelativeDuration(minutes?: number | null): string {
  if (!minutes) {
    return '--';
  }
  if (minutes < 60) {
    return `${minutes} 分钟`;
  }
  const hour = Math.floor(minutes / 60);
  const minute = minutes % 60;
  return minute === 0 ? `${hour} 小时` : `${hour} 小时 ${minute} 分钟`;
}
