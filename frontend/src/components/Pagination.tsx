type PaginationProps = {
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  label: string;
  onPageChange(page: number): void;
  onSizeChange(size: number): void;
};

export function Pagination({ page, size, totalPages, totalElements, label, onPageChange, onSizeChange }: PaginationProps) {
  const pages = pageNumbers(page, totalPages);

  return <nav className="pagination" aria-label={`${label} 페이지 탐색`}>
    <div className="pagination-summary">
      <span>{totalElements}건</span>
      <label>
        <span className="sr-only">페이지당 표시 건수</span>
        <select aria-label="페이지당 표시 건수" value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
          {[10, 20, 50].map((option) => <option key={option} value={option}>{option}건</option>)}
        </select>
      </label>
    </div>
    <div className="pagination-controls">
      <button className="pagination-button" aria-label="이전 페이지" disabled={page <= 0 || totalPages === 0} onClick={() => onPageChange(page - 1)}>‹</button>
      {pages.map((item, index) => item === 'ellipsis'
        ? <span className="pagination-ellipsis" key={`ellipsis-${index}`} aria-hidden="true">…</span>
        : <button key={item} className={`pagination-button${item === page ? ' active' : ''}`} aria-label={`${item + 1}페이지`} aria-current={item === page ? 'page' : undefined} onClick={() => onPageChange(item)}>{item + 1}</button>)}
      <button className="pagination-button" aria-label="다음 페이지" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>›</button>
    </div>
  </nav>;
}

function pageNumbers(page: number, totalPages: number): Array<number | 'ellipsis'> {
  if (totalPages <= 0) return [];
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index);

  const visible = new Set([0, totalPages - 1, page - 1, page, page + 1]);
  if (page <= 2) [1, 2, 3, 4].forEach((number) => visible.add(number));
  if (page >= totalPages - 3) [totalPages - 5, totalPages - 4, totalPages - 3, totalPages - 2].forEach((number) => visible.add(number));

  const numbers = [...visible].filter((number) => number >= 0 && number < totalPages).sort((left, right) => left - right);
  const items: Array<number | 'ellipsis'> = [];
  numbers.forEach((number, index) => {
    if (index > 0 && number - numbers[index - 1] > 1) items.push('ellipsis');
    items.push(number);
  });
  return items;
}
