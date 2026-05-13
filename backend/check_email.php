<?php
header("Content-Type: application/json");
error_reporting(E_ALL);
ini_set('display_errors', 1);

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;
use PHPMailer\PHPMailer\SMTP;

require 'PHPMailer/Exception.php';
require 'PHPMailer/PHPMailer.php';
require 'PHPMailer/SMTP.php';
require_once 'db_config.php';

$email = isset($_POST['email']) ? trim($_POST['email']) : null;

if (!$email) {
    echo json_encode(["success" => false, "message" => "Email is required"]);
    exit;
}

try {
    // 1. Cleanup: Auto-delete expired tokens for this email
    $now = date('Y-m-d H:i:s');
    $conn->prepare("UPDATE users SET reset_token = NULL, token_expiry = NULL WHERE email = ? AND token_expiry < ?")->execute([$email, $now]);
    $conn->prepare("UPDATE residents SET reset_token = NULL, token_expiry = NULL WHERE email = ? AND token_expiry < ?")->execute([$email, $now]);

    $stmt = $conn->prepare("SELECT name, email, 'users' as source FROM users WHERE email = ? UNION SELECT name, email, 'residents' as source FROM residents WHERE email = ?");
    $stmt->execute([$email, $email]);
    $account = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($account) {
        $name = $account['name'];
        $db_email = $account['email'];
        $source_table = $account['source'];

        $plain_token = strval(rand(100000, 999999));
        $hashed_token = password_hash($plain_token, PASSWORD_BCRYPT);

        // Set expiry to 3 minutes from now
        $expiry = date('Y-m-d H:i:s', strtotime('+3 minutes'));

        $updateQuery = "UPDATE $source_table SET reset_token = ?, token_expiry = ? WHERE email = ?";
        $updStmt = $conn->prepare($updateQuery);
        $updStmt->execute([$hashed_token, $expiry, $db_email]);

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
            $mail->addAddress($db_email, $name);
            $mail->isHTML(true);
            $mail->Subject = 'Your Verification Code';

            $mail->Body = "
                <div style='font-family: Arial, sans-serif; max-width: 600px; margin: auto; border: 1px solid #f0f0f0; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>
                    <div style='background-color: #1565C0; padding: 20px; text-align: center;'>
                        <h1 style='color: white; margin: 0; font-size: 24px;'>Garbage Truck Tracker</h1>
                    </div>
                    <div style='padding: 30px; background-color: white;'>
                        <p style='font-size: 16px; color: #333;'>Hello <strong>$name</strong>,</p>
                        <p style='font-size: 15px; color: #555; line-height: 1.5;'>We received a request to reset your password. Please use the verification code below to proceed:</p>

                        <div style='text-align: center; margin: 30px 0;'>
                            <div style='display: inline-block; background-color: #E3F2FD; border: 2px solid #1565C0; padding: 15px 40px; border-radius: 8px;'>
                                <span style='font-size: 36px; font-weight: bold; color: #1565C0; letter-spacing: 5px;'>$plain_token</span>
                            </div>
                        </div>

                        <p style='font-size: 14px; color: #777; line-height: 1.4;'>This code is valid for a limited time. If you did not request a password reset, you can safely ignore this email.</p>

                        <hr style='border: 0; border-top: 1px solid #eee; margin: 30px 0;'>

                        <div style='text-align: center; color: #999; font-size: 12px;'>
                            <p>© 2026 Garbage Truck Tracker System. All rights reserved.</p>
                        </div>
                    </div>
                </div>";

            $mail->send();
            echo json_encode(["success" => true, "message" => "Code sent successfully!"]);
        } catch (Exception $e) {
            echo json_encode(["success" => false, "message" => "Email failed: " . $mail->ErrorInfo]);
        }
    } else {
        echo json_encode(["success" => false, "message" => "Email not found."]);
    }
} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "DB Error: " . $e->getMessage()]);
}
?>
