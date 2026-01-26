package peerlinkfilesharingsystem.Service.MailService;


import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import peerlinkfilesharingsystem.Dto.ForgotPassword;
import peerlinkfilesharingsystem.Dto.RegistrationMailDTO;
import peerlinkfilesharingsystem.Dto.ShareFileResponse;
import peerlinkfilesharingsystem.Model.Otp;
import peerlinkfilesharingsystem.Model.Users;
import peerlinkfilesharingsystem.Repo.OtpRepo;
import peerlinkfilesharingsystem.Repo.UserRepo;
import peerlinkfilesharingsystem.Service.Jwt.JwtService;

import java.time.LocalDateTime;
import java.util.Random;



@Service
@Slf4j
public class MailServiceImpl implements MailService {
    private static final int OTP_LENGTH = 6;
    static final int OTP_EXPIRY_MINUTES = 5;

    @Autowired
    private  JavaMailSender mailSender;
    private final
    UserRepo userRepo;
    private final
    OtpRepo otpRepo;
    private final JwtService jwtService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    public MailServiceImpl(JavaMailSender mailSender, UserRepo userRepo, OtpRepo otpRepo,JwtService jwtService) {
        this.mailSender = mailSender;
        this.userRepo = userRepo;
        this.otpRepo = otpRepo;
        this.jwtService = jwtService;
    }

    @Override
    public boolean sendRegistrationMail(RegistrationMailDTO registrationMailDTO) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            log.info("Users in Mail Service" +registrationMailDTO );

            helper.setFrom(fromEmail);
            helper.setTo(registrationMailDTO.getEmail());
            helper.setSubject("Welcome to PeerLink – Registration Successful");

            String htmlContent = """
                <html>
                    <body style="font-family: Arial; padding: 20px; background-color: #f4f4f4;">
                        <div style="max-width: 500px; margin: auto; background: white; padding: 20px; border-radius: 8px;">
                            <h2 style="color: #333;">🎉 Registration Successful</h2>
                            
                            <p>Hello <b>%s</b>,</p>
                            
                            <p>Your account has been successfully created.</p>

                            <h3 style="color: #444;">Your Details:</h3>
                            <table style="width:100%%; border-collapse: collapse;">
                                <tr>
                                    <td style="padding: 8px; border-bottom: 1px solid #ddd;">Username:</td>
                                    <td style="padding: 8px; border-bottom: 1px solid #ddd;"><b>%s</b></td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px; border-bottom: 1px solid #ddd;">Email:</td>
                                    <td style="padding: 8px; border-bottom: 1px solid #ddd;"><b>%s</b></td>
                                </tr>
                            </table>

                            <p style="margin-top: 20px;">You can now login and start using the app.</p>

                            <br>
                            <p>Regards,<br>PeerLink Team</p>
                        </div>
                    </body>
                </html>
                """.formatted(
                    registrationMailDTO.getUsername(),
                    registrationMailDTO.getUsername(),
                    registrationMailDTO.getEmail()
            );

            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info(" Mail Sent " +registrationMailDTO );

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    @Override
    public boolean sendLoginMail(RegistrationMailDTO registrationMailDTO) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(registrationMailDTO.getEmail());
            helper.setSubject("Login Successful");

            String html = String.format(
                    "<html>" +
                            "<body>" +
                            "<div style='max-width:500px;margin:auto;padding:20px;border:1px solid #eee;border-radius:10px;font-family:Arial'>" +
                            "<h2 style='text-align:center;color:#333;'>Login Successful</h2>" +

                            "<p>Hello <b>%s</b>,</p>" +
                            "<p>You have successfully logged into your account.</p>" +

                            "<p><b>Login Details:</b><br>" +
                            "Email: %s<br>" +
                            "Login Time: %s<br></p>" +

                            "<p>If this was not you, please reset your password immediately.</p>" +
                            "<br>" +
                            "<p style='font-size:12px;color:#777;text-align:center;'>© 2025 PeerLink File Sharing System</p>" +
                            "</div>" +
                            "</body></html>",
                    registrationMailDTO.getUsername(),
                    registrationMailDTO.getEmail(),
                    LocalDateTime.now().toString()
            );

            helper.setText(html, true);

            mailSender.send(mimeMessage);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean sendLinkToMail(ShareFileResponse response, String email) {

        String htmlContent = "<html><body>" +
                "<h2>Your File is Ready</h2>" +
                "<p><strong>File:</strong> " + response.getFileName() + "</p>" +
                "<p><strong>Share ID:</strong> " + response.getShareport() + "</p>" +
                "<p><strong>Download Link:</strong> <a href='" + response.getFileDownloadUri() + "'>Click Here</a></p>" +
                "</body></html>";
        try {
                 MimeMessage message = mailSender.createMimeMessage();
                 MimeMessageHelper helper = new MimeMessageHelper(message, true);

                helper.setTo(email);
                helper.setSubject("Your File Download Link");
                helper.setText(htmlContent, true);

                mailSender.send(message);
        }catch (Exception e) {
                return false;
        }
        return true;
    }

    public ResponseEntity<?> SendOtp(String email) {
        Users user = userRepo.findByEmail(email);
        if (user == null) {
            return new ResponseEntity<>("Mail Not Found", HttpStatus.NOT_FOUND);
        }
        Otp otpexist = otpRepo.findByUser(user);
        if (otpexist!= null) {
            SimpleMailMessage message1 = new SimpleMailMessage();
            message1.setFrom(fromEmail);
            message1.setTo(email);
            message1.setSubject("Otp For Forgot Password");
            message1.setText(String.format("Hello! %s,\n" +
                    "Your otp for Forgot Password \n" +
                    "OTP : '%s' ,\n" +
                    "expires at %s",user.getUsername(), otpexist.getOtp(),otpexist.getOtpTime()));
            mailSender.send(message1);

            return new ResponseEntity<>("Otp Already Sent Please try After Some Time", HttpStatus.OK);
        }

        String otp = generateOtp();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Otp For Forgot Password");
        message.setText(String.format("Hello! %s,\n"+
                "Your otp for Forgot Password is : '%s'\n"+
                "expires in 5 mins.",user.getUsername(),otp));
        mailSender.send(message);
        otp(user,otp);
        return ResponseEntity.ok("Otp Sent to "+email);
    }

    public void otp(Users users, String otp) {
        Otp otpEntity = new Otp();
        otpEntity.setOtp(otp);
        otpEntity.setUser(users);
        otpEntity.setOtpTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpRepo.save(otpEntity);
    }

    public String generateOtp() {
        Random random = new Random();
        int otpNumber = random.nextInt((int) Math.pow(10, OTP_LENGTH));
        return String.format("%0" + OTP_LENGTH + "d", otpNumber);
    }

    public ResponseEntity<?> ValidateOtp(String otp,String username) {
        Otp otpEntity = otpRepo.findByOtpAndUser(otp,username);
        if (otpEntity == null) {
            return new ResponseEntity<>("Invalid Otp", HttpStatus.NOT_FOUND);
        }
        if (otpEntity.getOtpTime().isBefore(LocalDateTime.now())) {
            otpRepo.delete(otpEntity);
            return new ResponseEntity<>("Otp Expired", HttpStatus.BAD_REQUEST);
        }
        if (otpEntity.getOtp().equals(otp)) {
            otpRepo.delete(otpEntity);
            if (jwtService!= null){
                String token = jwtService.generateTempToken(username);
                return new ResponseEntity<>(token, HttpStatus.OK);
            }
            else {
                return new ResponseEntity<>("Unable to Process", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return null;
    }






    private Users retriveLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !authentication.isAuthenticated())
            throw new BadCredentialsException("Bad Credentials login ");
        String username = authentication.getName();
//        System.out.println(STR."In Logged In User \{username}");
        System.out.println("Logged In User "+username);
        Users user = userRepo.findByUsername(username);
        if(user == null){
            throw new UsernameNotFoundException("User Not Found");
        }
        return user;
    }

    public ResponseEntity<?> forgotpassword(ForgotPassword forgotPassword) {
        Users user = retriveLoggedInUser();
        if (user == null) {
            return new ResponseEntity<>("Unable to process", HttpStatus.GATEWAY_TIMEOUT);
        }
        if (forgotPassword.getPassword().equals(forgotPassword.getReenterpassword())) {
            user.setPassword(encoder.encode(forgotPassword.getPassword()));
            userRepo.save(user);
            return new ResponseEntity<>("Password Changed Successfully", HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>("Passwords don't match", HttpStatus.BAD_REQUEST);
        }
    }

}
