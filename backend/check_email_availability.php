<?php
header("Content-Type: application/json");
require_once 'db_config.php';

$email = $_POST['email'] ?? null;

if ($email) {
    try {
        // Check ONLY in main tables (approved users/residents)
        $query1 = "SELECT 1 FROM users WHERE email = ?";
        $stmt1 = $conn->prepare($query1);
        $stmt1->execute([$email]);

        $query2 = "SELECT 1 FROM residents WHERE email = ?";
        $stmt2 = $conn->prepare($query2);
        $stmt2->execute([$email]);

        if ($stmt1->fetch() || $stmt2->fetch()) {
            // success = true means it IS registered (not available)
            echo json_encode(["success" => true, "message" => "Email is already registered"]);
        } else {
            // success = false means it is NOT in main tables (available for registration)
            // even if it exists in registration_requests as pending or declined
            echo json_encode(["success" => false, "message" => "Email is available"]);
        }
    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "No email provided"]);
}
?>
