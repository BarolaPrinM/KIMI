<?php
header("Content-Type: application/json");
require_once 'db_config.php';

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;
use PHPMailer\PHPMailer\SMTP;

require 'PHPMailer/Exception.php';
require 'PHPMailer/PHPMailer.php';
require 'PHPMailer/SMTP.php';

// Support both JSON and Form Data
$input = file_get_contents("php://input");
$data = json_decode($input);

$email = $_POST['email'] ?? $data->email ?? null;
$name = $_POST['name'] ?? $data->name ?? null;
$role = $_POST['role'] ?? $data->role ?? 'User';
$username = $_POST['username'] ?? $data->username ?? null;

if ($email && $name) {
    try {
        // Update status in MySQL if username is provided
        if ($username) {
            $stmt = $conn->prepare("UPDATE registration_requests SET status = 'declined' WHERE username = ?");
            $stmt->execute([$username]);
        }

        $mail = new PHPMailer(true);
        $mail->isSMTP();
        $mail->Host       = 'smtp.gmail.com';
        $mail->SMTPAuth   = true;
        $mail->Username   = 'garbagetrucktrackersystem@gmail.com';
        $mail->Password   = 'xlrv nkcz pjpa uxiz';
        $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
        $mail->Port       = 587;

        $mail->setFrom('garbagetrucktrackersystem@gmail.com', 'Garbage Truck Tracker');
        $mail->addAddress($email, $name);
        $mail->isHTML(true);
        $mail->Subject = 'Registration Update - Garbage Truck Tracker';

        $roleDisplay = ucfirst($role);

        $mail->Body = "
            <div style='font-family: Arial, sans-serif; max-width: 600px; margin: auto; border: 1px solid #f0f0f0; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>
                <div style='background-color: #D32F2F; padding: 20px; text-align: center;'>
                    <h1 style='color: white; margin: 0; font-size: 24px;'>Garbage Truck Tracker</h1>
                </div>
                <div style='padding: 30px; background-color: white;'>
                    <p style='font-size: 16px; color: #333;'>Hello <strong>$name</strong>,</p>
                    <p style='font-size: 15px; color: #555; line-height: 1.5;'>We regret to inform you that your registration request as a <strong>$roleDisplay</strong> has been <strong>DECLINED</strong> after review.</p>

                    <p style='font-size: 15px; color: #555; line-height: 1.5;'>This could be due to incomplete information or other administrative reasons. If you believe this is a mistake, you may try registering again with accurate details.</p>

                    <div style='text-align: center; margin: 30px 0;'>
                        <div style='display: inline-block; background-color: #FFEBEE; border: 2px solid #D32F2F; padding: 15px 40px; border-radius: 8px;'>
                            <span style='font-size: 20px; font-weight: bold; color: #D32F2F;'>Status: DECLINED</span>
                        </div>
                    </div>

                    <p style='font-size: 14px; color: #777; line-height: 1.4;'>Thank you for your interest in our system.</p>

                    <hr style='border: 0; border-top: 1px solid #eee; margin: 30px 0;'>

                    <div style='text-align: center; color: #999; font-size: 12px;'>
                        <p>© 2026 Garbage Truck Tracker System. All rights reserved.</p>
                    </div>
                </div>
            </div>";

        $mail->send();
        echo json_encode(["success" => true, "message" => "Request declined and email sent"]);

    } catch (Exception $e) {
        echo json_encode(["success" => false, "message" => "Process failed: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Missing email or name."]);
}
?>
