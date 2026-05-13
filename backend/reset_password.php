<?php
header("Content-Type: application/json");

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;
use PHPMailer\PHPMailer\SMTP;

require 'PHPMailer/Exception.php';
require 'PHPMailer/PHPMailer.php';
require 'PHPMailer/SMTP.php';
require_once 'db_config.php';

$email = isset($_POST['email']) ? trim($_POST['email']) : null;
$password = isset($_POST['password']) ? $_POST['password'] : null;

if ($email && $password) {
    try {
        $hashed_password = password_hash($password, PASSWORD_BCRYPT);

        // Fetch source table and name
        $stmt = $conn->prepare("SELECT name, 'users' as source FROM users WHERE email = ? UNION SELECT name, 'residents' as source FROM residents WHERE email = ?");
        $stmt->execute([$email, $email]);
        $account = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($account) {
            $name = $account['name'];
            $source_table = $account['source'];

            // Update the correct table
            $updateQuery = "UPDATE $source_table SET password_hash = ?, reset_token = NULL, token_expiry = NULL WHERE email = ?";
            $updStmt = $conn->prepare($updateQuery);
            $updStmt->execute([$hashed_password, $email]);

            // rowCount >= 0 because if they use the same password, it returns 0 but it's not an error
            // Send Success Email
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
                $mail->addAddress($email, $name);
                $mail->isHTML(true);
                $mail->Subject = 'Password Reset Successful';

                $mail->Body = "
                    <div style='font-family: Arial, sans-serif; max-width: 600px; margin: auto; border: 1px solid #f0f0f0; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 5px rgba(0,0,0,0.1);'>
                        <div style='background-color: #2E7D32; padding: 20px; text-align: center;'>
                            <h1 style='color: white; margin: 0; font-size: 24px;'>Garbage Truck Tracker</h1>
                        </div>
                        <div style='padding: 30px; background-color: white;'>
                            <p style='font-size: 16px; color: #333;'>Hello <strong>$name</strong>,</p>
                            <p style='font-size: 15px; color: #555; line-height: 1.5;'>Your password has been successfully reset. You can now use your new password to log in to your account.</p>

                            <div style='text-align: center; margin: 30px 0;'>
                                <div style='display: inline-block; background-color: #E8F5E9; color: #2E7D32; padding: 15px 30px; border-radius: 8px; font-weight: bold; border: 2px solid #2E7D32;'>
                                    Password Reset Successful
                                </div>
                            </div>

                            <p style='font-size: 14px; color: #777; line-height: 1.4;'>If you did not perform this action, please contact our support team immediately to secure your account.</p>

                            <hr style='border: 0; border-top: 1px solid #eee; margin: 30px 0;'>

                            <div style='text-align: center; color: #999; font-size: 12px;'>
                                <p>© 2026 Garbage Truck Tracker System. All rights reserved.</p>
                            </div>
                        </div>
                    </div>";

                $mail->send();
            } catch (Exception $e) {
                // Ignore email failure, password is still reset
            }

            echo json_encode(["success" => true, "message" => "Password updated successfully"]);
        } else {
            echo json_encode(["success" => false, "message" => "Account not found with this email."]);
        }
    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Email and password are required"]);
}
?>
