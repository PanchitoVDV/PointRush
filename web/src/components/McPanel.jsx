export default function McPanel({ title, icon, children, className = '', accent }) {
  return (
    <section
      className={`mc-panel ${className}`}
      style={accent ? { '--panel-accent': accent } : undefined}
    >
      {title && (
        <header className="panel-header">
          {icon && <span className="panel-icon">{icon}</span>}
          <h2>{title}</h2>
        </header>
      )}
      <div className="panel-body">{children}</div>
    </section>
  );
}
