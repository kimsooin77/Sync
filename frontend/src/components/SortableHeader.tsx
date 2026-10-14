type SortDirection = 'ASC' | 'DESC';

export function SortableHeader({ label, sortKey, activeSort, onSort }: {
  label: string;
  sortKey: string;
  activeSort: { sortBy: string; sortDirection: SortDirection } | null;
  onSort: (key: string) => void;
}) {
  const active = activeSort?.sortBy === sortKey;
  const direction = active ? activeSort.sortDirection : null;
  return <th aria-sort={direction === 'ASC' ? 'ascending' : direction === 'DESC' ? 'descending' : 'none'}>
    <button className="sortable-header" type="button" onClick={() => onSort(sortKey)}>
      <span>{label}</span><span className={`sort-indicator${active ? ' active' : ''}`} aria-hidden="true">
        {direction === 'ASC' ? '▲' : direction === 'DESC' ? '▼' : '↕'}
      </span>
    </button>
  </th>;
}
