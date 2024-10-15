package com.example.demo;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("admin/")
public class AdminController {
    @Autowired
    private ProductService productService;
    private static final String UPLOAD_DIR = "src/main/resources/static/images/";

    @GetMapping("products")
    public String getAllProducts(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user != null && user.getType().equals("admin")) {
            List<Product> products = productService.getAllProduct();
            model.addAttribute("products", products);
            model.addAttribute("user", user);
            return "product-list";  // Update to the correct view name
        } else {
            return "redirect:/";
        }
    }

    @GetMapping("add-product")
    public String addProductForm(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user != null && user.getType().equals("admin")) {
            model.addAttribute("product", null);
            model.addAttribute("user", user);
            return "add-product"; // Update to the correct view name
        } else {
            return "redirect:/";
        }
    }

    @PostMapping("add-product")
    public String addProduct(@RequestParam String productName, @RequestParam String price,
                             @RequestParam MultipartFile imageFile, Model model, HttpSession session) throws IOException {
        User user = (User) session.getAttribute("user");
        if (user != null && user.getType().equals("admin")) {



            String imageName = imageFile.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + imageName);
            Files.createDirectories(path.getParent()); // Ensure directory exists
            Files.write(path, imageFile.getBytes());

            Product product = new Product();
            product.setProductName(productName);
            product.setPrice(price);
            product.setImage(imageName);

            productService.saveProduct(product);
            model.addAttribute("user", user);
            return "redirect:/admin/products"; // Update redirect path
        } else {
            return "redirect:/";
        }
    }

    @GetMapping("update-product")
    public String updateProductForm(@RequestParam Long id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null && user.getType().equals("admin")) {
            Product product = productService.getProductById(id);
            model.addAttribute("product", product);
            model.addAttribute("user", user);
            return "add-product"; // Reuse the add-product form for updates
        } else {
            return "redirect:/";
        }
    }

    @PostMapping("update-product-done")
    public String updateProduct(@RequestParam Long pid, @RequestParam String productName,
                                @RequestParam String price, @RequestParam(required = false) MultipartFile imageFile,
                                HttpSession session) throws IOException {
        User user = (User) session.getAttribute("user");
        if (user != null && user.getType().equals("admin")) {
            Product existingProduct = productService.getProductById(pid);
            existingProduct.setProductName(productName);
            existingProduct.setPrice(price);

            // Handle new image upload
            if (imageFile != null && !imageFile.isEmpty()) {
                // Delete old image if exists
                String oldImageName = existingProduct.getImage();
                Path oldImagePath = Paths.get(UPLOAD_DIR + oldImageName);
                if (Files.exists(oldImagePath)) {
                    Files.delete(oldImagePath);
                }
                // Save new image
                String newImageName = imageFile.getOriginalFilename();
                Path newPath = Paths.get(UPLOAD_DIR + newImageName);
                Files.write(newPath, imageFile.getBytes());
                existingProduct.setImage(newImageName);
            }

            productService.saveProduct(existingProduct);
            return "redirect:/admin/products"; // Update redirect path
        } else {
            return "redirect:/";
        }
    }

    @GetMapping("delete-product")
    public String deleteProduct(@RequestParam Long id, HttpSession session) throws IOException {
        User user = (User) session.getAttribute("user");
        if (user != null && user.getType().equals("admin")) {
            Product product = productService.getProductById(id);
            // Delete the associated image file
            String imageName = product.getImage();
            Path imagePath = Paths.get(UPLOAD_DIR + imageName);
            if (Files.exists(imagePath)) {
                Files.delete(imagePath);
            }
            productService.deleteProduct(id); // Ensure the product is deleted from the database
            return "redirect:/admin/products"; // Update redirect path
        } else {
            return "redirect:/";
        }
    }


    @Autowired
    private OrderRepository orderRepository;

    // Method to display all orders (as before)
    @GetMapping("/orders")
    public String viewOrders(Model model) {
        List<Order> orders = orderRepository.findAllByOrderByOrderDateDesc();
        model.addAttribute("orders", orders);
        return "admin-order-history"; // Thymeleaf template for displaying all orders
    }


    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

    @GetMapping("/order-details/{orderId}")
    public String orderDetails(@PathVariable Long orderId, Model model) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Invalid order Id:" + orderId));
        List<OrderHistory> orderHistoryList = orderHistoryRepository.findByOrder(order);

        model.addAttribute("orderHistoryList", orderHistoryList);
        model.addAttribute("order", order);
        return "order-details";
    }


    @PostMapping("/order/update")
    public String updateOrderStatus(@RequestParam("orderId") Long orderId,
                                    @RequestParam("status") String status) {
        Optional<Order> optionalOrder = orderRepository.findById(orderId);
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.setStatus(status);
            orderRepository.save(order);
        }
        return "redirect:/admin/orders"; // Redirect to the orders page after updating
    }
}
