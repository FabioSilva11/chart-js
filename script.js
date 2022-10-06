const ctx = document.getElementById('myChart').getContext('2d');

const urlParams = new URLSearchParams(window.location.search);
const database = urlParams.get ("w");

const url= new URLSearchParams(window.location.search);
const win = url.get ("w");

const urlP = new URLSearchParams(window.location.search);
const loss = urlP.get ("l");

const Params = new URLSearchParams(window.location.search);
const gale1 = Params.get ("g1");

const ams = new URLSearchParams(window.location.search);
const gale2 = ams.get ("g2");

const myChart = new Chart(ctx, {

    type: 'line',
    data: {
        labels: ['WIN', 'LOSS', 'GALE-1', 'GALE-2'],
        datasets: [{
            label: 'DESENPENHO DO ROBO',
            data: [win, loss, gale1, gale2],
            backgroundColor: [
                'rgba(255, 99, 132, 0.2)',
                'rgba(54, 162, 235, 0.2)',
                'rgba(255, 206, 86, 0.2)',
                'rgba(75, 192, 192, 0.2)',
                'rgba(153, 102, 255, 0.2)',
                'rgba(235, 159, 644, 0.2)',
                'rgba(255, 10, 12, 0.2)'
            ],
            borderColor: [
                'rgba(255, 99, 132, 1)',
                'rgba(54, 162, 235, 1)',
                'rgba(255, 206, 86, 1)',
                'rgba(75, 192, 192, 1)',
                'rgba(153, 102, 255, 1)',
                'rgba(255, 159, 64, 1)',
                'rgba(255, 10, 12, 0.2)'
            ],
            borderWidth: 1
        }]
    },
    options: {
        scales: {
            y: {
                beginAtZero: true
            }
        }
    }
    
});
