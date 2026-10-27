/* Dependency-free SVG charts. Colours follow the portal palette. */

const CHART_PALETTE = ['#818cf8', '#22d3ee', '#f472b6', '#fbbf24', '#34d399', '#a78bfa'];
const CHART_NS = 'http://www.w3.org/2000/svg';

function chartSvgEl(name, attrs = {}) {
    const node = document.createElementNS(CHART_NS, name);
    Object.entries(attrs).forEach(([key, value]) => {
        if (value !== null && value !== undefined) {
            node.setAttribute(key, String(value));
        }
    });
    return node;
}

function chartEmptyHtml(message) {
    return `<div class="chart-empty"><span>${escapeHtml(message)}</span></div>`;
}

function niceStep(raw) {
    const value = raw > 0 ? raw : 1;
    const exponent = Math.floor(Math.log10(value));
    const base = Math.pow(10, exponent);
    const fraction = value / base;
    const factor = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
    return factor * base;
}

/**
 * Line chart with optional dashed "failure" series.
 * options: { labels: string[], series: [{ label, color, values: number[], dashed?: boolean }], ariaLabel }
 */
function renderLineChart(container, { labels, series, ariaLabel }) {
    if (!container) {
        return;
    }
    const hasData =
        labels.length > 0 &&
        series.length > 0 &&
        series.some((line) => line.values.some((value) => Number(value) > 0));

    if (!hasData) {
        container.innerHTML = chartEmptyHtml('No notifications in this period.');
        return;
    }

    const width = 760;
    const height = 240;
    const pad = { top: 16, right: 16, bottom: 30, left: 46 };
    const innerWidth = width - pad.left - pad.right;
    const innerHeight = height - pad.top - pad.bottom;

    const peak = Math.max(1, ...series.flatMap((line) => line.values.map((value) => Number(value) || 0)));
    const step = Math.max(1, Math.ceil(niceStep(peak / 4)));
    const maxValue = step * 4;

    const xFor = (index) =>
        labels.length === 1
            ? pad.left + innerWidth / 2
            : pad.left + (index * innerWidth) / (labels.length - 1);
    const yFor = (value) => pad.top + innerHeight * (1 - (Number(value) || 0) / maxValue);

    const svg = chartSvgEl('svg', {
        class: 'chart',
        viewBox: `0 0 ${width} ${height}`,
        role: 'img',
        'aria-label': ariaLabel || 'Notification volume chart'
    });

    for (let tick = 0; tick <= 4; tick += 1) {
        const value = step * tick;
        const y = yFor(value);
        svg.append(
            chartSvgEl('line', {
                class: 'grid-line',
                x1: pad.left,
                x2: width - pad.right,
                y1: y,
                y2: y
            })
        );
        const label = chartSvgEl('text', {
            class: 'axis-label',
            x: pad.left - 8,
            y: y + 3,
            'text-anchor': 'end'
        });
        label.textContent = String(Math.round(value));
        svg.append(label);
    }

    const labelEvery = Math.max(1, Math.ceil(labels.length / 6));
    labels.forEach((label, index) => {
        const isLast = index === labels.length - 1;
        if (index % labelEvery !== 0 && !isLast) {
            return;
        }
        const text = chartSvgEl('text', {
            class: 'axis-label',
            x: xFor(index),
            y: height - 8,
            'text-anchor': index === 0 ? 'start' : isLast ? 'end' : 'middle'
        });
        text.textContent = label;
        svg.append(text);
    });

    series.forEach((line) => {
        const points = line.values.map((value, index) => [xFor(index), yFor(value)]);
        const path = points
            .map(([x, y], index) => `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`)
            .join(' ');

        if (series.length === 1) {
            const area = `M${points[0][0].toFixed(2)},${(pad.top + innerHeight).toFixed(2)} ${points
                .map(([x, y]) => `L${x.toFixed(2)},${y.toFixed(2)}`)
                .join(' ')} L${points[points.length - 1][0].toFixed(2)},${(
                pad.top + innerHeight
            ).toFixed(2)} Z`;
            svg.append(
                chartSvgEl('path', {
                    d: area,
                    fill: line.color,
                    opacity: 0.12
                })
            );
        }

        svg.append(
            chartSvgEl('path', {
                class: 'series-line',
                d: path,
                stroke: line.color,
                'stroke-dasharray': line.dashed ? '5 4' : null
            })
        );

        if (labels.length <= 32) {
            points.forEach(([x, y], index) => {
                const dot = chartSvgEl('circle', {
                    cx: x,
                    cy: y,
                    r: 2.6,
                    fill: line.color
                });
                const title = chartSvgEl('title');
                title.textContent = `${labels[index]} — ${line.label}: ${line.values[index]}`;
                dot.append(title);
                svg.append(dot);
            });
        }
    });

    container.replaceChildren(svg);
}

/**
 * Donut chart. options: { items: [{ label, value, color }], centerValue, centerLabel, ariaLabel }
 */
function renderDonut(container, { items, centerValue, centerLabel, ariaLabel }) {
    if (!container) {
        return;
    }
    const visible = (items || []).filter((item) => Number(item.value) > 0);
    if (visible.length === 0) {
        container.innerHTML = chartEmptyHtml('Nothing to show yet.');
        return;
    }

    const total = visible.reduce((sum, item) => sum + Number(item.value), 0);
    const radius = 62;
    const circumference = 2 * Math.PI * radius;
    let offset = 0;

    const segments = visible
        .map((item, index) => {
            const length = (Number(item.value) / total) * circumference;
            const color = item.color || CHART_PALETTE[index % CHART_PALETTE.length];
            const circle = `<circle cx="90" cy="90" r="${radius}" fill="none"
                stroke="${color}" stroke-width="16"
                stroke-dasharray="${length.toFixed(2)} ${(circumference - length).toFixed(2)}"
                stroke-dashoffset="${(-offset).toFixed(2)}"
                transform="rotate(-90 90 90)"><title>${escapeHtml(item.label)}: ${formatCount(
                item.value
            )}</title></circle>`;
            offset += length;
            return circle;
        })
        .join('');

    const legend = visible
        .map((item, index) => {
            const percent = Math.round((Number(item.value) / total) * 1000) / 10;
            return `<li>
                <i style="background:${item.color || CHART_PALETTE[index % CHART_PALETTE.length]}"></i>
                <span class="donut-name">${escapeHtml(item.label)}</span>
                <span class="donut-value">${formatCount(item.value)}</span>
                <span class="donut-pct">${percent}%</span>
            </li>`;
        })
        .join('');

    container.innerHTML = `
        <figure class="donut-figure" role="img" aria-label="${escapeHtml(
            ariaLabel || 'Distribution'
        )}">
            <svg viewBox="0 0 180 180" class="donut" aria-hidden="true">
                <circle cx="90" cy="90" r="${radius}" fill="none" stroke="var(--surface-3)" stroke-width="16"/>
                ${segments}
                <text x="90" y="86" text-anchor="middle" class="donut-center-value">${escapeHtml(
                    centerValue === undefined ? formatCount(total) : String(centerValue)
                )}</text>
                <text x="90" y="104" text-anchor="middle" class="donut-center-label">${escapeHtml(
                    centerLabel || 'total'
                )}</text>
            </svg>
            <figcaption>
                <ul class="donut-legend">${legend}</ul>
            </figcaption>
        </figure>`;
}

/**
 * Horizontal bar list. options: { items: [{ label, value, color }], emptyText }
 */
function renderBarList(container, { items, emptyText }) {
    if (!container) {
        return;
    }
    const visible = (items || []).filter((item) => Number(item.value) > 0);
    if (visible.length === 0) {
        container.innerHTML = chartEmptyHtml(emptyText || 'No data in this period.');
        return;
    }
    const total = visible.reduce((sum, item) => sum + Number(item.value), 0);

    container.innerHTML = visible
        .map((item, index) => {
            const percent = (Number(item.value) / total) * 100;
            return `<div class="bar-row">
                <div class="bar-label">
                    <span>${escapeHtml(item.label)}</span>
                    <strong>${formatCount(item.value)}</strong>
                </div>
                <div class="bar-track" role="presentation">
                    <span class="bar-fill" style="width:${percent.toFixed(1)}%;background:${
                        item.color || CHART_PALETTE[index % CHART_PALETTE.length]
                    }"></span>
                </div>
                <span class="bar-pct">${percent.toFixed(0)}%</span>
            </div>`;
        })
        .join('');
}
