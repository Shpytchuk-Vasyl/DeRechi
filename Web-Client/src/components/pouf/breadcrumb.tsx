interface BreadcrumbItem {
  label: string
  href?: string
}

interface BreadcrumbProps {
  items: BreadcrumbItem[]
}

export function Breadcrumb({ items }: BreadcrumbProps) {
  if (items.length === 0) return null

  return (
    <nav aria-label="Breadcrumb">
      <ol className="pouf-breadcrumb">
        {items.map((item, i) => {
          const last = i === items.length - 1
          return (
            <li key={i} className="pouf-breadcrumb__item">
              {i > 0 && (
                <span className="pouf-breadcrumb__sep" aria-hidden="true">
                  /
                </span>
              )}
              {last || !item.href ? (
                <span className="pouf-breadcrumb__current" aria-current={last ? 'page' : undefined}>
                  {item.label}
                </span>
              ) : (
                <a className="pouf-breadcrumb__link" href={item.href}>
                  {item.label}
                </a>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
