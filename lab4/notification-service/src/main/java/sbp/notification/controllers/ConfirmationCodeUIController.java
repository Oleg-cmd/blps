package sbp.notification.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import sbp.notification.services.ConfirmationCodeDisplayService;

@Controller
@RequestMapping("/ui/codes")
@RequiredArgsConstructor
public class ConfirmationCodeUIController {

  private final ConfirmationCodeDisplayService confirmationCodeDisplayService;

  @GetMapping
  public String showActiveCodes(Model model) {
    List<ConfirmationCodeDisplayService.DisplayedCode> activeCodes =
      confirmationCodeDisplayService.getAllActiveCodes();
    model.addAttribute("activeCodes", activeCodes);
    return "active-codes"; // Имя Thymeleaf шаблона (active-codes.html)
  }
}
