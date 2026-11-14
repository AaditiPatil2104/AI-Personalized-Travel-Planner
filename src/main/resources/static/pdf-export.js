/* YatraAI PDF export – builds real, downloadable PDF files (works in the browser and in Node for tests). */
(function (root) {
  const GREEN = [23, 102, 75], MINT = [229, 245, 235], INK = [23, 55, 43], MUTED = [103, 128, 116];
  const MAP = { '₹': 'Rs.', '–': '-', '—': '-', '’': "'", '‘': "'", '“': '"', '”': '"', '•': '-', '→': '>', '…': '...', 'œ': 'oe', 'Œ': 'OE', '×': 'x', '✓': '', '✕': '', 'ʼ': "'" };
  function t(v) {
    let s = String(v == null ? '' : v), o = '';
    for (const ch of s) {
      const c = ch.codePointAt(0);
      if (MAP[ch] !== undefined) o += MAP[ch];
      else if ((c >= 32 && c <= 126) || (c >= 160 && c <= 255)) o += ch;
      else { const b = ch.normalize('NFD').replace(/[\u0300-\u036f]/g, ''); o += /^[\x20-\x7e]+$/.test(b) ? b : ''; }
    }
    return o.replace(/\s+/g, ' ').trim();
  }
  const rs = n => 'Rs. ' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 });
  const fdate = d => { if (!d) return ''; const x = new Date(String(d).slice(0, 10) + 'T00:00:00'); return isNaN(x) ? String(d) : x.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }); };

  function footer(doc, label) {
    const n = doc.internal.getNumberOfPages(), W = doc.internal.pageSize.getWidth(), H = doc.internal.pageSize.getHeight();
    for (let i = 1; i <= n; i++) {
      doc.setPage(i); doc.setFontSize(8); doc.setTextColor(...MUTED); doc.setFont('helvetica', 'normal');
      doc.text(t(label), 14, H - 8); doc.text('Page ' + i + ' of ' + n, W - 14, H - 8, { align: 'right' });
    }
  }
  function banner(doc, title, sub) {
    const W = doc.internal.pageSize.getWidth();
    doc.setFillColor(...GREEN); doc.rect(0, 0, W, 30, 'F');
    doc.setTextColor(255, 255, 255); doc.setFont('helvetica', 'bold'); doc.setFontSize(18); doc.text(t(title), 14, 14);
    doc.setFont('helvetica', 'normal'); doc.setFontSize(10); doc.text(t(sub), 14, 22);
    doc.setTextColor(...INK);
  }
  function h(doc, text, y) { doc.setFont('helvetica', 'bold'); doc.setFontSize(12); doc.setTextColor(...GREEN); doc.text(t(text), 14, y); doc.setTextColor(...INK); return y + 3; }
  const base = { theme: 'grid', styles: { font: 'helvetica', fontSize: 8.5, cellPadding: 2, textColor: INK, lineColor: [212, 231, 219] }, headStyles: { fillColor: GREEN, textColor: 255 }, alternateRowStyles: { fillColor: [247, 252, 249] }, margin: { left: 14, right: 14, bottom: 14 } };
  const endY = doc => doc.lastAutoTable.finalY;

  function buildPlanPdf(jsPDF, p) {
    const doc = new jsPDF({ unit: 'mm', format: 'a4' });
    const dest = p.destination || 'Trip';
    banner(doc, 'YatraAI - Travel Plan: ' + dest, (p.startLocation || '') + ' to ' + dest + '  |  ' + (p.startDate ? fdate(p.startDate) + ' - ' + fdate(p.endDate) : p.days + ' days') + '  |  ' + (p.travelers || 0) + ' traveller(s)');
    let y = 38;
    const g = p.group || {}, br = p.breakdown || {};
    y = h(doc, 'Trip summary', y);
    doc.autoTable({ ...base, startY: y, body: [
      ['Route', t(p.startLocation) + ' > ' + t(dest) + (p.country ? ' (' + t(p.country) + ')' : '')],
      ['Dates', p.startDate ? fdate(p.startDate) + ' to ' + fdate(p.endDate) + '  (' + p.days + ' days, ' + (p.nights ?? Math.max(0, p.days - 1)) + ' nights)' : p.days + ' days'],
      ['Travellers', (p.adults ?? p.travelers) + ' adult(s)' + (p.children ? ', ' + p.children + ' child(ren)' : '')],
      ['Rooms', g.summary ? t(g.summary) : '-'],
      ['Interests', t(p.interests).replace(/,\s*/g, ', ')], ['Travel mode / style', t(p.transport) + ' / ' + t(p.travelStyle)], ['Food / hotel standard', t(p.foodPreference) + ' / ' + t(p.hotelTier || p.hotelStandard || '-')],
    ], columnStyles: { 0: { fontStyle: 'bold', cellWidth: 42, fillColor: MINT } }, showHead: false });
    y = endY(doc) + 8;
    y = h(doc, 'Budget', y);
    const rows = [['Intercity travel (return)', rs(br.intercity ?? p.intercityTravel)], ['Hotels', rs(br.stay ?? p.hotelCost)], ['Food', rs(br.food ?? p.foodCost)], ['Local transport', rs(br.local ?? p.localTravelCost)], ['Entry fees & activities', rs(br.activities ?? p.activitiesCost)]];
    if (br.visa || p.visaCost) rows.push(['Visa & insurance (approx.)', rs(br.visa ?? p.visaCost)]);
    rows.push(['ESTIMATED TOTAL', rs(p.estimatedCost)], ['Your budget', rs(p.budget)], ['Remaining / (over)', rs(Number(p.budget) - Number(p.estimatedCost))], ['Suggested 10% buffer', rs(p.emergencyBuffer)]);
    doc.autoTable({ ...base, startY: y, body: rows, showHead: false, columnStyles: { 0: { cellWidth: 90 }, 1: { halign: 'right', fontStyle: 'bold' } },
      didParseCell: d => { if (d.row.index === 5 + (br.visa || p.visaCost ? 1 : 0)) { d.cell.styles.fillColor = MINT; } } });
    y = endY(doc) + 8;
    if ((g.roomPlan || []).length) {
      if (y > 235) { doc.addPage(); y = 20; }
      y = h(doc, 'Group & room allocation (' + t(g.summary || '') + ')', y);
      doc.autoTable({ ...base, startY: y, head: [['Room', 'Who stays', 'Room type']], body: g.roomPlan.map(r => ['Room ' + r.room, t(r.label.replace(/^Room \d+:\s*/, '')), r.type]) });
      y = endY(doc) + 4;
      (g.tips || []).forEach(x => { if (y > 280) { doc.addPage(); y = 20; } doc.setFontSize(8); doc.setTextColor(...MUTED); const L = doc.splitTextToSize('- ' + t(x), 180); doc.text(L, 14, y); y += L.length * 4; });
      y += 4;
    }
    const be = (p.booking && p.booking.entries) || [];
    if (be.length) {
      if (y > 220) { doc.addPage(); y = 20; }
      y = h(doc, 'Hotel booking plan (rates are planning estimates)', y);
      doc.autoTable({ ...base, startY: y, head: [['Night', 'Check-in > out', 'Hotel', 'Category', 'Rooms x rate', 'Night total']],
        body: be.map(e => [e.night, e.checkIn ? fdate(e.checkIn) + ' > ' + fdate(e.checkOut) : '-', t(e.name) + '\n' + t(e.address), e.tierLabel, e.rooms + ' x ' + rs(e.rate), rs(e.nightCost)]),
        columnStyles: { 2: { cellWidth: 62 }, 5: { halign: 'right' } } });
      y = endY(doc) + 4;
      doc.setFontSize(8); doc.setTextColor(...MUTED); doc.text('Total stay: ' + rs(p.hotelCost) + '. Always confirm live rates and availability with the hotel before paying.', 14, y); y += 8;
    }
    (p.daysPlan || []).forEach(day => {
      doc.addPage(); banner(doc, 'Day ' + day.day + ' - ' + t(day.areaName || day.hotelArea || day.title), (day.date ? fdate(day.date) + '  |  ' : '') + t(day.title));
      let yy = 38;
      if (day.excursionNote) { doc.setFontSize(9); doc.setTextColor(150, 90, 20); const L = doc.splitTextToSize(t(day.excursionNote), 180); doc.text(L, 14, yy); yy += L.length * 4.5 + 2; doc.setTextColor(...INK); }
      doc.autoTable({ ...base, startY: yy, head: [['Time', 'Place', 'Type', 'Visit', 'Cost (group)']],
        body: (day.places || []).flatMap(a => [[a.time, { content: t(a.name), styles: { fontStyle: 'bold' } }, t(a.category), t(a.duration), rs(a.cost)], [{ content: t(a.description) + '\n' + t(a.distanceFromPrevious), colSpan: 5, styles: { textColor: MUTED, fontSize: 7.5 } }]]),
        columnStyles: { 4: { halign: 'right' } } });
      yy = endY(doc) + 6;
      const hot = day.hotel;
      doc.setFontSize(10); doc.setFont('helvetica', 'bold'); doc.setTextColor(...GREEN);
      if (hot) {
        doc.text('Tonight\'s stay', 14, yy); yy += 3;
        doc.autoTable({ ...base, startY: yy, showHead: false, body: [[{ content: t(hot.name) + '  (' + hot.tierLabel + ')\n' + t(hot.address), styles: { fontStyle: 'bold' } }, rs(hot.rate) + ' x ' + hot.rooms + ' room(s) = ' + rs(hot.nightCost)]], columnStyles: { 1: { halign: 'right', cellWidth: 70 } } });
        yy = endY(doc) + 3;
        const alts = (day.hotelOptions || []).filter(o => !o.selected);
        if (alts.length) { doc.setFontSize(8); doc.setFont('helvetica', 'normal'); doc.setTextColor(...MUTED); const L = doc.splitTextToSize('Other real options nearby: ' + alts.map(o => t(o.name) + ' (' + o.tierLabel + ', ~' + rs(o.rate) + '/room)').join('; '), 180); doc.text(L, 14, yy + 2); yy += L.length * 4 + 4; }
      } else if (day.checkoutFrom) { doc.text('Check-out from ' + t(day.checkoutFrom) + ' and head home.', 14, yy); yy += 6; }
      doc.setFont('helvetica', 'normal'); doc.setTextColor(...INK); doc.setFontSize(9);
      doc.text('Day costs - activities ' + rs(day.activitiesCost) + ' | local transport ' + rs(day.localTransportCost) + ' | food ' + rs(day.foodCost) + (hot ? ' | hotel ' + rs(day.hotelNightCost) : ''), 14, yy + 3);
    });
    doc.addPage(); banner(doc, 'Before you travel', dest);
    let ny = 40; doc.setFontSize(10);
    ((p.warnings || []).concat(p.tips || [])).forEach(x => { const L = doc.splitTextToSize('- ' + t(x), 180); doc.text(L, 14, ny); ny += L.length * 5 + 2; });
    footer(doc, 'YatraAI - planning estimates only, not live booking quotes');
    return doc;
  }

  function buildReportPdf(jsPDF, r, adminName) {
    const doc = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'landscape' });
    const titles = { all: 'All trips', date: 'Date-wise', user: 'Traveller-wise', place: 'Place-wise', city: 'City-wise' };
    banner(doc, 'YatraAI - ' + (titles[r.type] || 'Trip') + ' Report', 'Generated ' + new Date().toLocaleString('en-GB') + (adminName ? ' by ' + t(adminName) : ''));
    let y = 38; const f = [];
    if (r.value) f.push('Filter: ' + t(r.value)); if (r.from) f.push('From: ' + fdate(r.from)); if (r.to) f.push('To: ' + fdate(r.to));
    doc.setFontSize(10); doc.text(f.length ? f.join('   |   ') : 'No filters (all records)', 14, y); y += 4;
    doc.autoTable({ ...base, startY: y, head: [['Trips', 'Travellers', 'Total budget', 'Total estimated cost', 'Average estimate']], body: [[r.count, r.totalTravellers, rs(r.totalBudget), rs(r.totalEstimate), rs(r.averageEstimate)]], styles: { ...base.styles, fontSize: 10, halign: 'center' } });
    y = endY(doc) + 6;
    doc.autoTable({ ...base, startY: y, head: [['#', 'Booked on', 'Traveller', 'Email', 'From', 'Destination', 'Start date', 'Days', 'Adults+Kids', 'Rooms', 'Budget', 'Estimate']],
      body: (r.rows || []).map((x, i) => [i + 1, fdate(x.createdAt), t(x.userName), t(x.userEmail), t(x.startLocation), t(x.destination), fdate(x.startDate), x.days, (x.adults ?? x.travelers) + '+' + (x.children || 0), x.rooms || '-', rs(x.budget), rs(x.estimatedCost)]),
      styles: { ...base.styles, fontSize: 8 }, columnStyles: { 10: { halign: 'right' }, 11: { halign: 'right' } } });
    if (!(r.rows || []).length) { doc.setFontSize(11); doc.text('No trips match these filters.', 14, endY(doc) + 10); }
    footer(doc, 'YatraAI admin report');
    return doc;
  }
  const api = { buildPlanPdf, buildReportPdf, sanitize: t };
  if (typeof module === 'object' && module.exports) module.exports = api; else root.YatraPdf = api;
})(typeof window !== 'undefined' ? window : globalThis);
