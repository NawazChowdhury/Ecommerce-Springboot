package com.example.demo;


import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/")
public class HomeController {


    @Autowired
    private UserService userService;

    private final UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

    @Autowired
    private ProductService productService;




    public HomeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        List<CartItem> cartItems = (List<CartItem>) session.getAttribute("cartItems");
        if (cartItems == null) {
            cartItems = new ArrayList<>();
        }
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartQty", cartItems.size());
        model.addAttribute("total", calculateTotal(cartItems));

        List<Product> products = productService.getAllProduct();
        model.addAttribute("products", products);
        return "home";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam String item, @RequestParam String image, @RequestParam double price, HttpSession session) {
        List<CartItem> cartItems = (List<CartItem>) session.getAttribute("cartItems");
        if (cartItems == null) {
            cartItems = new ArrayList<>();
            session.setAttribute("cartItems", cartItems);
        }

        boolean itemExists = false;
        for (CartItem cartItem : cartItems) {
            if (cartItem.getName().equals(item)) {
                cartItem.setQuantity(cartItem.getQuantity() + 1);
                itemExists = true;
                break;
            }
        }

        if (!itemExists) {
            cartItems.add(new CartItem(item, image, price));
        }

        return "redirect:/";
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam int index, HttpSession session) {
        List<CartItem> cartItems = (List<CartItem>) session.getAttribute("cartItems");
        if (cartItems != null && index >= 0 && index < cartItems.size()) {
            cartItems.remove(index);
        }
        return "redirect:/cart";
    }

    private double calculateTotal(List<CartItem> cartItems) {
        double total = 0;
        for (CartItem item : cartItems) {
            total += item.getTotalAmount();
        }
        return total;
    }

    @GetMapping("/cart")
    public String cart(HttpSession session, Model model) {
        List<CartItem> cartItems = (List<CartItem>) session.getAttribute("cartItems");
        if (cartItems == null) {
            cartItems = new ArrayList<>();
        }
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartQty", cartItems.size());

        double totalAmount = calculateTotal(cartItems);
        DecimalFormat df = new DecimalFormat("#0.00");
        String formattedTotal = df.format(totalAmount);

        model.addAttribute("total",formattedTotal);


        return "cart";
    }

    @GetMapping("/order")
    public String order(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        // Retrieve cart items from session
        List<CartItem> cartItems = (List<CartItem>) session.getAttribute("cartItems");
        if (cartItems == null || cartItems.isEmpty()) {
            return "redirect:/cart"; // If no items in cart, redirect to cart
        }

        // Calculate total amount
        double totalAmount = calculateTotal(cartItems);

        // Create new order
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(totalAmount);
        order.setOrderDate(new Date());
        order.setOrderHistoryList(new ArrayList<>());

        // Save order to DB first (so we get an order ID)
        Order savedOrder = orderRepository.save(order);

        // Create order history for each cart item
        for (CartItem cartItem : cartItems) {
            OrderHistory orderHistory = new OrderHistory();
            orderHistory.setOrder(savedOrder);
            orderHistory.setProductName(cartItem.getName());
            orderHistory.setProductImage(cartItem.getImage());
            orderHistory.setProductPrice(cartItem.getPrice());
            orderHistory.setQuantity(cartItem.getQuantity());

            // Save each order history to DB
            orderHistoryRepository.save(orderHistory);
        }

        // Clear the cart
        session.removeAttribute("cartItems");

        // Redirect to success page
        return "redirect:/success";
    }

    @GetMapping("/success")
    public String success(HttpSession session, Model model) {
        return "success";
    }

    @GetMapping("/my-orders")
    public String viewOrders(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        List<Order> orders = orderRepository.findByUserOrderByOrderDateDesc(user);
        model.addAttribute("orders", orders);
        return "order-history"; // Create a view for displaying orders
    }

    @GetMapping("/order-details/{orderId}")
    public String orderDetails(@PathVariable Long orderId, Model model) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Invalid order Id:" + orderId));
        List<OrderHistory> orderHistoryList = orderHistoryRepository.findByOrder(order);

        model.addAttribute("orderHistoryList", orderHistoryList);
        model.addAttribute("order", order);
        return "order-details";
    }




    @GetMapping("/signup")
    public String addUserForm(Model model) {
        // model.addAttribute("user", new User());
        return "signup";
    }

    @PostMapping("/signup-v")
    public String saveUser(@ModelAttribute("user") User user, @RequestParam("vpassword") String vpassword, Model model, HttpSession session) {


        // Step 1: Check if any field is empty
        if (user.getName().isEmpty() || user.getEmail().isEmpty() || user.getPassword().isEmpty() || vpassword.isEmpty()) {
            model.addAttribute("error", "All fields are required");
            return "signup"; // Return the signup page with an error message
        }

        // Step 2: Validate email format (simple regex)
        if (!user.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            model.addAttribute("error", "Invalid email format");
            return "signup";
        }

        // Step 3: Validate password length
        if (user.getPassword().length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters long");
            return "signup";
        }

        // Step 4: Check if password and verify password match
        if (!user.getPassword().equals(vpassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "signup";
        }

        // Step 5: Save user if validation passes
        userService.saveUser(user);
        //return "redirect:/login";
        //model.addAttribute("success", "User Created Successfully");
        session.setAttribute("message", "User Created Successfully");
        //return "signup";
        return "redirect:/login";
    }




    @GetMapping("/login")
    public String index() {
        return "login";
    }
    @PostMapping("/login-v")
    public String login(@RequestParam String email, @RequestParam String
            password, HttpSession session, Model model) {
        User user = userRepository.findByEmail(email);
        if (user != null && user.getPassword().equals(password)) {
            session.setAttribute("user", user);

            if(user.getType().equals("admin")){
                return "redirect:/admin/products";
            }else if(user.getType().equals("user")){
                return "redirect:/dashboard";
            }
            //  model.addAttribute("error", user.getType());
            return "login";
        } else {
            model.addAttribute("error", "Invalid email or password");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("user");
        return "redirect:/";
    }
}