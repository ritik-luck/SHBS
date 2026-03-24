import { Line } from "react-chartjs-2";

function FailureChart({data}) {

  const chartData = {
    labels: data.map((d,i)=>i),
    datasets: [
      {
        label: "Failures",
        data: data,
        borderColor: "red"
      }
    ]
  };

  return <Line data={chartData} />;
}

export default FailureChart;