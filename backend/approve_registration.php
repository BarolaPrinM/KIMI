<?php
header("Content-Type: application/json");
require_once 'db_config.php';

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;
use PHPMailer\PHPMailer\SMTP;

require 'PHPMailer/Exception.php';
require 'PHPMailer/PHPMailer.php';
require 'PHPMailer/SMTP.php';

$data = json_decode(file_get_contents("php://input"));

if (!$data || empty($data->username)) {
    echo json_encode(["success" => false, "message" => "Invalid data"]);
    exit;
}

try {
    $conn->beginTransaction();

    $username = $data->username;
    $role = $data->role;

    // 1. Update status in registration_requests
    $stmt = $conn->prepare("UPDATE registration_requests SET status = 'approved' WHERE username = ?");
    $stmt->execute([$username]);

    // 2. Insert into the appropriate table
    if ($role === 'resident') {
        $query = "INSERT INTO residents (username, name, password_hash, email, phone, purok, complete_address, status)
                  SELECT username, name, password_hash, email, phone, purok, complete_address, 'approved'
                  FROM registration_requests WHERE username = ?";
        $stmt = $conn->prepare($query);
        $stmt->execute([$username]);
    } else {
        $query = "INSERT INTO users (username, name, email, password_hash, phone, license_number, preferred_truck, role, status)
                  SELECT username, name, email, password_hash, phone, license_number, preferred_truck, role, 'approved'
                  FROM registration_requests WHERE username = ?";
        $stmt = $conn->prepare($query);
        $stmt->execute([$username]);
    }

    $conn->commit();

    // 3. Send Approval Email
    if (!empty($data->email)) {
        sendApprovalEmail($data->email, $data->name, $role);
    }

    echo json_encode(["success" => true, "message" => "Registration approved successfully"]);

} catch (Exception $e) {
    if ($conn->inTransaction()) $conn->rollBack();
    echo json_encode(["success" => false, "message" => "Error: " . $e->getMessage()]);
}

function sendApprovalEmail($recipientEmail, $recipientName, $role) {
    $mail = new PHPMailer(true);
    try {
        $mail->isSMTP();
        $mail->Host       = 'smtp.gmail.com';
        $mail->SMTPAuth   = true;
        $mail->Username   = 'garbagetrucktrackersystem@gmail.com';
        $mail->Password   = 'xlrv nkcz pjpa uxiz';
        $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
        $mail->Port       = 587;

        $mail->setFrom('garbagetrucktrackersystem@gmail.com', 'Garbage Truck Tracker');
        $mail->addAddress($recipientEmail, $recipientName);
        $mail->isHTML(true);
        $mail->Subject = 'Registration Approved - Garbage Truck Tracker';

        $roleDisplay = ucfirst($role);

        $mail->Body = "
            <div style='font-family: Arial, sans-serif; max-width: 600px; margin: auto; border: 1px solid #f0f0f0; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>
                <div style='background-color: #2E7D32; padding: 20px; text-align: center;'>
                    <h1 style='color: white; margin: 0; font-size: 24px;'>Garbage Truck Tracker</h1>
                </div>
                <div style='padding: 30px; background-color: white;'>
                    <p style='font-size: 16px; color: #333;'>Hello <strong>$recipientName</strong>,</p>
                    <p style='font-size: 15px; color: #555; line-height: 1.5;'>We are pleased to inform you that your registration as a <strong>$roleDisplay</strong> has been <strong>APPROVED</strong> by the administrator.</p>

                    <p style='font-size: 15px; color: #555; line-height: 1.5;'>You can now log in to the Garbage Truck Tracker application using your registered credentials.</p>

                    <div style='text-align: center; margin: 30px 0;'>
                        <div style='display: inline-block; background-color: #E8F5E9; border: 2px solid #2E7D32; padding: 15px 40px; border-radius: 8px;'>
                            <span style='font-size: 20px; font-weight: bold; color: #2E7D32;'>Status: APPROVED</span>
                        </div>
                    </div>

                    <p style='font-size: 14px; color: #777; line-height: 1.4;'>Welcome to our community! If you have any questions, feel free to contact us through the app.</p>

                    <hr style='border: 0; border-top: 1px solid #eee; margin: 30px 0;'>

                    <div style='text-align: center; color: #999; font-size: 12px;'>
                        <p>© 2026 Garbage Truck Tracker System. All rights reserved.</p>
                    </div>
                </div>
            </div>";

        $mail->send();
    } catch (Exception $e) {
        // Log error
    }
}
?>
