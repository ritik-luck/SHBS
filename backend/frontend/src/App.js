import React, { useState, useCallback } from "react";
import "./App.css";
import Sidebar from "./components/Sidebar";
import Dashboard from "./Dashboard";

function App() {
  const [activePage, setActivePage] = useState("dashboard");
  const [healthData, setHealthData] = useState({ score: 100, status: "HEALTHY" });

  const handleHealthUpdate = useCallback((data) => {
    setHealthData(data);
  }, []);

  return (
    <div className="app-layout">
      <Sidebar
        activePage={activePage}
        onNavigate={setActivePage}
        healthData={healthData}
        alertCount={0}
      />
      <main className="main-content">
        <Dashboard
          activePage={activePage}
          onHealthUpdate={handleHealthUpdate}
        />
      </main>
    </div>
  );
}

export default App;