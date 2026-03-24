import React, { useEffect, useState } from "react";
import { getFailures, getServiceFailures, getSystemState } from "./api";
import { Line } from "react-chartjs-2";
import { Client } from "@stomp/stompjs";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
} from "chart.js";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement
);

const Dashboard = () => {
  const [alert, setAlert] = useState("NORMAL");
  const [failures, setFailures] = useState(0);
  const [state, setState] = useState("");
  const [services, setServices] = useState([]);

  const loadData = async () => {
    try {
      const f = await getFailures();
      const s = await getSystemState();
      const svc = await getServiceFailures();

      setFailures(f.data);
      setState(s.data);
      setServices(svc.data);
    } catch (error) {
      console.error("Error loading data:", error);
    }
  };

  useEffect(() => {
    loadData();
    const client = new Client({
      brokerURL: "ws://localhost:8080/ws",
      reconnectDelay: 5000,
    });

    client.onConnect = () => {
      client.subscribe("/topic/alerts", (message) => {
        console.log("Alert:", message.body);
        setAlert(message.body);
      });
      client.subscribe("/topic/metrics", (message) => {
        console.log("WebSocket event received:", message);
        loadData();
      });
    };

    client.activate();

    return () => {
      client.deactivate();
    };
  }, []);

 const chartData = {
   labels: services.map((s) => s[0]),
   datasets: [
     {
       label: "Service Failures",
       data: services.map((s) => s[1]),
       fill: false,
       borderColor: "red",
     },
   ],
 };

  return (
    <div style={{ padding: "20px", background: "#0f172a", color: "white", minHeight: "100vh" }}>
      <h1>Self Healing Dashboard</h1>

      <h2>System State: {state}</h2>
      <h3>Total Failures: {failures}</h3>

      <h3>Service Failures</h3>
      {services.map((s, i) => (
        <div key={i}>
          {s[0]} : {s[1]}
        </div>
      ))}

      <div style={{ marginTop: "20px" }}>
        <Line key={JSON.stringify(chartData)} data={chartData} />
      </div>

      <div style={{ marginTop: "30px", display: "flex", gap: "10px" }}>
        <button
          onClick={async () => {
            await fetch("http://localhost:8080/simulate-failure");
            loadData();
          }}
          style={{
            backgroundColor: "#ef4444",
            color: "white",
            padding: "10px 15px",
            border: "none",
            borderRadius: "5px",
            cursor: "pointer"
          }}
        >
          Simulate Failure
        </button>

        <button
          onClick={async () => {
            await fetch("http://localhost:8080/admin/reset-circuit", {
              method: "POST",
            });
            loadData();
          }}
          style={{
            backgroundColor: "#22c55e",
            color: "white",
            padding: "10px 15px",
            border: "none",
            borderRadius: "5px",
            cursor: "pointer"
          }}
        >
          Reset Circuit
        </button>

        <button
          onClick={async () => {
            await fetch("http://localhost:8080/admin/clear-metrics", {
              method: "POST",
            });
            loadData();
          }}
          style={{
            backgroundColor: "#facc15",
            color: "black",
            padding: "10px 15px",
            border: "none",
            borderRadius: "5px",
            cursor: "pointer"
          }}
        >
          Clear Metrics
        </button>
      </div>
    </div>
  );
};

export default Dashboard;