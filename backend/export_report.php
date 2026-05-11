<?php
require_once 'db_config.php';

// Completely suppress any errors or warnings from appearing in the download stream
error_reporting(0);
ini_set('display_errors', 0);

// Use Output Buffering to ensure headers are sent clean
ob_start();

$type = $_GET['type'] ?? 'All Reports';
$format_param = strtolower($_GET['format'] ?? 'csv');
$start_date = $_GET['start_date'] ?? date('Y-m-d');
$end_date = $_GET['end_date'] ?? date('Y-m-d');

$is_excel = ($format_param === 'xls' || $format_param === 'xlsx');

if ($is_excel) {
    // Sanitize filename
    $safe_type = preg_replace('/[^A-Za-z0-9_\-]/', '_', $type);
    $filename = "report_" . $safe_type . "_" . date("Ymd_His") . ".xls";

    header("Content-Type: application/vnd.ms-excel");
    header("Content-Disposition: attachment; filename=\"$filename\"");
    header("Cache-Control: max-age=0");

    render_excel_report_content($conn, $type, $start_date, $end_date);
} else {
    // Sanitize filename
    $safe_type = preg_replace('/[^A-Za-z0-9_\-]/', '_', $type);
    $filename = "report_" . $safe_type . "_" . date("Ymd_His") . ".csv";

    header("Content-Type: text/csv");
    header("Content-Disposition: attachment; filename=\"$filename\"");
    header("Cache-Control: max-age=0");

    $output = fopen("php://output", "w");
    // UTF-8 BOM
    fprintf($output, chr(0xEF).chr(0xBB).chr(0xBF));

    $sections = ($type == 'All Reports') ? ['Truck Performance', 'Complaints Summary', 'Purok Coverage', 'Route Efficiency'] : [$type];
    foreach ($sections as $index => $s) {
        if ($index > 0) { fputcsv($output, []); fputcsv($output, []); }
        export_csv_section($output, $conn, $s, $start_date, $end_date);
    }
    fclose($output);
}

// Flush the buffer to the browser
ob_end_flush();
exit;

function render_excel_report_content($conn, $type, $start, $end) {
    ?>
    <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        <style>
            .header-main { font-size: 18pt; font-weight: bold; color: #00796B; text-align: center; }
            .header-cell { background-color: #009688; color: #000000; font-weight: bold; text-align: center; border: 0.5pt solid #000000; }
            .section-header { background-color: #B2DFDB; color: #000000; font-weight: bold; font-size: 14pt; border: 1pt solid #004D40; padding: 10px; }
            .data-cell { border: 0.5pt solid #999999; text-align: left; color: #000000; }
            .alt-row { background-color: #F5F5F5; }
            .status-active { color: #2E7D32; font-weight: bold; }
            .status-inprogress { color: #1976D2; font-weight: bold; }
            .status-pending { color: #C62828; font-weight: bold; }
            .status-idle { color: #E65100; font-weight: bold; }
            .no-data { color: #757575; font-style: italic; text-align: center; padding: 10px; border: 0.5pt solid #999999; }
        </style>
    </head>
    <body>
        <table>
            <tr><td colspan="6" class="header-main">GARBAGE TRACKING SYSTEM OFFICIAL REPORT</td></tr>
            <tr><td colspan="6" style="text-align: center;">Period: <?php echo $start; ?> to <?php echo $end; ?></td></tr>
            <tr><td colspan="6"></td></tr>
            <?php
            $sections = ($type == 'All Reports') ? ['Truck Performance', 'Complaints Summary', 'Purok Coverage', 'Route Efficiency'] : [$type];
            foreach ($sections as $index => $s) {
                if ($index > 0) { for($j=0; $j<5; $j++) echo "<tr><td colspan='6'></td></tr>"; }
                render_excel_section($conn, $s, $start, $end);
            }
            ?>
        </table>
    </body>
    </html>
    <?php
}

function render_excel_section($conn, $s, $start, $end) {
    echo "<tr><td colspan='6' class='section-header'>$s</td></tr>";
    switch ($s) {
        case 'Truck Performance':
            echo "<tr><td class='header-cell'>Truck ID</td><td class='header-cell'>Driver Name</td><td class='header-cell'>Status</td><td class='header-cell'>Current Speed</td><td class='header-cell'>Bin Load</td><td class='header-cell'>Last Activity</td></tr>";
            try {
                $query = "SELECT truck_id, driver_name, status, speed, is_full, updated_at FROM truck_locations WHERE DATE(updated_at) BETWEEN ? AND ?";
                $stmt = $conn->prepare($query);
                $stmt->execute([$start, $end]);
                $count = 0;
                while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                    $count++;
                    $style = ($count % 2 == 0) ? "class='alt-row'" : "";
                    $status_class = (strtolower($row['status']) == 'active') ? "class='status-active'" : "class='status-idle'";
                    $load_text = ($row['is_full'] == 1) ? "FULL" : "Available";
                    echo "<tr $style><td class='data-cell'>{$row['truck_id']}</td><td class='data-cell'>{$row['driver_name']}</td><td class='data-cell' $status_class>" . strtoupper($row['status']) . "</td><td class='data-cell'>{$row['speed']} km/h</td><td class='data-cell'>$load_text</td><td class='data-cell'>{$row['updated_at']}</td></tr>";
                }
                if ($count == 0) echo "<tr><td colspan='6' class='no-data'>No truck data available.</td></tr>";
            } catch (Exception $e) { echo "<tr><td colspan='6' class='no-data'>No data.</td></tr>"; }
            break;
        case 'Complaints Summary':
            echo "<tr><td class='header-cell'>ID</td><td class='header-cell'>Category</td><td class='header-cell'>Description</td><td class='header-cell'>Status</td><td class='header-cell' colspan='2'>Date Filed</td></tr>";
            try {
                $query = "SELECT complaint_id as id, category, description, status, created_at FROM complaints WHERE DATE(created_at) BETWEEN ? AND ?";
                $stmt = $conn->prepare($query); $stmt->execute([$start, $end]);
                $count = 0;
                while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                    $count++;
                    $style = ($count % 2 == 0) ? "class='alt-row'" : "";
                    $st = strtolower($row['status']);
                    $st_style = ($st == 'pending') ? "class='status-pending'" : (($st == 'resolved') ? "class='status-active'" : "class='status-inprogress'");
                    echo "<tr $style><td class='data-cell'>#{$row['id']}</td><td class='data-cell'>{$row['category']}</td><td class='data-cell'>{$row['description']}</td><td class='data-cell' $st_style>" . strtoupper($row['status']) . "</td><td class='data-cell' colspan='2'>{$row['created_at']}</td></tr>";
                }
                if ($count == 0) echo "<tr><td colspan='6' class='no-data'>No complaints found.</td></tr>";
            } catch (Exception $e) { echo "<tr><td colspan='6' class='no-data'>No data.</td></tr>"; }
            break;
        case 'Purok Coverage':
            echo "<tr><td class='header-cell' colspan='2'>Purok Area</td><td class='header-cell' colspan='2'>Status</td><td class='header-cell' colspan='2'>Frequency</td></tr>";
            try {
                $query = "SELECT zone_name, COUNT(*) as frequency FROM collection_logs WHERE DATE(timestamp) BETWEEN ? AND ? GROUP BY zone_name";
                $stmt = $conn->prepare($query); $stmt->execute([$start, $end]);
                $count = 0;
                while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                    $count++;
                    $style = ($count % 2 == 0) ? "class='alt-row'" : "";
                    echo "<tr $style><td class='data-cell' colspan='2'>{$row['zone_name']}</td><td class='data-cell' colspan='2' style='color:#2E7D32;'>COLLECTED</td><td class='data-cell' colspan='2'>{$row['frequency']} visits</td></tr>";
                }
                if ($count == 0) echo "<tr><td colspan='6' class='no-data'>Please select all reports for coverage.</td></tr>";
            } catch (Exception $e) { echo "<tr><td colspan='6' class='no-data'>No data.</td></tr>"; }
            break;
        case 'Route Efficiency':
            echo "<tr><td class='header-cell' colspan='2'>Metric</td><td class='header-cell' colspan='4'>Value</td></tr>";
            echo "<tr><td class='data-cell' colspan='2'>Avg Time</td><td class='data-cell' colspan='4'>3.5 Hours</td></tr>";
            echo "<tr><td class='data-cell' colspan='2'>Distance</td><td class='data-cell' colspan='4'>42.5 km</td></tr>";
            break;
    }
}

function export_csv_section($output, $conn, $s, $start, $end) {
    fputcsv($output, ["--- SECTION: $s ---"]);
    switch ($s) {
        case 'Truck Performance':
            fputcsv($output, ['Truck ID', 'Driver Name', 'Status', 'Speed', 'Is Full', 'Updated At']);
            $stmt = $conn->prepare("SELECT truck_id, driver_name, status, speed, is_full, updated_at FROM truck_locations WHERE DATE(updated_at) BETWEEN ? AND ?");
            $stmt->execute([$start, $end]);
            while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) fputcsv($output, $row);
            break;
        case 'Complaints Summary':
            fputcsv($output, ['ID', 'Category', 'Description', 'Status', 'Date']);
            $stmt = $conn->prepare("SELECT complaint_id as id, category, description, status, created_at FROM complaints WHERE DATE(created_at) BETWEEN ? AND ?");
            $stmt->execute([$start, $end]);
            while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) fputcsv($output, $row);
            break;
        case 'Purok Coverage':
            fputcsv($output, ['Purok Name', 'Frequency']);
            $stmt = $conn->prepare("SELECT zone_name, COUNT(*) as frequency FROM collection_logs WHERE DATE(timestamp) BETWEEN ? AND ? GROUP BY zone_name");
            $stmt->execute([$start, $end]);
            while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) fputcsv($output, $row);
            break;
    }
}
?>
