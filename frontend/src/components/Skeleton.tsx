import type { CSSProperties, ReactNode } from 'react';

type SkeletonProps = { width?: number | string; height?: number | string; className?: string };

export function Skeleton({ width = '100%', height = 12, className = '' }: SkeletonProps) {
  const style: CSSProperties = { width, height };
  return <span aria-hidden="true" className={`skeleton ${className}`.trim()} style={style} />;
}

const tableWidths = [
  ['46%', '76%', '52%', '62%', '48%', '18%'],
  ['58%', '64%', '70%', '45%', '57%', '18%'],
  ['40%', '82%', '60%', '54%', '68%', '18%'],
  ['52%', '70%', '45%', '72%', '50%', '18%'],
  ['64%', '58%', '66%', '48%', '60%', '18%'],
  ['44%', '74%', '54%', '62%', '46%', '18%'],
  ['56%', '68%', '72%', '50%', '64%', '18%'],
  ['48%', '78%', '58%', '66%', '52%', '18%'],
];

export function SkeletonTableRows({ rows = 7, columns }: { rows?: number; columns: number }) {
  return <>
    {Array.from({ length: rows }, (_, row) => <tr key={row} aria-hidden="true" className="skeleton-table-row">
      {Array.from({ length: columns }, (_, column) => <td key={column}><Skeleton width={tableWidths[row % tableWidths.length][column % 6]} /></td>)}
    </tr>)}
  </>;
}

export function SkeletonTimeline({ events = 3 }: { events?: number }) {
  return <div aria-hidden="true" className="skeleton-timeline">
    {Array.from({ length: events }, (_, index) => <div className="skeleton-timeline-event" key={index}>
      <Skeleton width={9} height={9} className="skeleton-dot" />
      <div><Skeleton width={index === 1 ? '36%' : '28%'} /><Skeleton width={index === 2 ? '48%' : '42%'} height={10} /></div>
    </div>)}
  </div>;
}

export function SkeletonRegion({ label, className = '', children }: { label: string; className?: string; children: ReactNode }) {
  return <div aria-busy="true" className={`skeleton-region ${className}`.trim()}>
    <span className="visually-hidden" role="status">{label}</span>
    {children}
  </div>;
}
