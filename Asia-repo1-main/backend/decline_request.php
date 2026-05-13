<?php
header("Content-Type: application/json");
require_once 'db_config.php';

// If you have PHPMailer, you can uncomment these
/*
use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;
use PHPMailer\PHPMailer\SMTP;

require 'PHPMailer/Exception.php';
require 'PHPMailer/PHPMailer.php';
require 'PHPMailer/SMTP.php';
*/

$data = json_decode(file_get_contents("php://input"));

if (!$data) {
    echo json_encode(["success" => false, "message" => "No data received"]);
    exit;
}

if (!empty($data->username) && !empty($data->role)) {
    try {
        $name = !empty($data->name) ? $data->name : $data->username;
        $email = !empty($data->email) ? $data->email : "";
        $status = "declined";

        if ($data->role === 'resident') {
            $query = "INSERT INTO residents (username, name, email, phone, purok, complete_address, status)
                      VALUES (?, ?, ?, ?, ?, ?, ?)";
            $stmt = $conn->prepare($query);
            $phone = !empty($data->phone) ? $data->phone : null;
            $purok = !empty($data->purok) ? $data->purok : "";
            $address = !empty($data->complete_address) ? $data->complete_address : "";

            $stmt->execute([$data->username, $name, $email, $phone, $purok, $address, $status]);
        } else {
            $query = "INSERT INTO users (username, name, email, phone, license_number, preferred_truck, role, status)
                      VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            $stmt = $conn->prepare($query);
            $phone = !empty($data->phone) ? $data->phone : null;
            $license = !empty($data->license_number) ? $data->license_number : "";
            $truck = !empty($data->preferred_truck) ? $data->preferred_truck : null;

            $stmt->execute([$data->username, $name, $email, $phone, $license, $truck, $data->role, $status]);
        }

        // Logic to send decline email would go here (similar to the one in outer folder)

        echo json_encode(["success" => true, "message" => "Request declined and recorded in database"]);

    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Incomplete data"]);
}
?>
