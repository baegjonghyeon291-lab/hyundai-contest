import vehicles from './mock/vehicles.json'
import './App.css'

function App() {
  return (
    <div className="phone">
      <header className="app-header">내 차</header>
      <main className="app-body">
        {vehicles.map((v) => (
          <div key={v.id} className="card">
            <strong>{v.model}</strong>
            <span>{v.plate}</span>
            <span>
              주행 {v.mileage.toLocaleString()}km
              {v.battery !== null && ` · 배터리 ${v.battery}%`}
            </span>
          </div>
        ))}
      </main>
    </div>
  )
}

export default App
